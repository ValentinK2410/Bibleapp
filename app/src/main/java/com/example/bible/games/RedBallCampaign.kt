package com.example.bible.games

import android.content.Context
import com.example.bible.games.skins.RedBallSkinId

/**
 * Десять длинных уровней. У флага сразу открывается следующий.
 * Свободная горка и редактор живут отдельно и этот список не заменяют.
 */
object RedBallCampaign {
    const val COUNT = 10

    val levels: List<RedBallCampaignLevel> = listOf(
        levelHills(),
        levelFakeFlag(),
        levelFriend(),
        levelDarkForest(),
        levelPortal(),
        levelCartChase(),
        levelCollapseWater(),
        levelFlood(),
        levelFlee(),
        levelBoss(),
    )

    fun byIndex(index: Int): RedBallCampaignLevel? = levels.firstOrNull { it.index == index }

    fun byId(id: String): RedBallCampaignLevel? = levels.firstOrNull { it.id == id || it.scene.id == id }

    /** Сцена, если портал ведёт в уровень кампании, а не в сохранение редактора. */
    fun sceneByLink(linkId: String): RedBallScene? = byId(linkId)?.scene

    fun isCampaignId(id: String): Boolean = byId(id) != null
}

data class RedBallCampaignLevel(
    val index: Int,
    val id: String,
    val title: String,
    val chapter: String,
    val skin: RedBallSkinId,
    val intro: String,
    val twist: String,
    val scene: RedBallScene,
    /** Куски уровня по порядку. Игра их не рисует: это карта для тестов и описания. */
    val sections: List<String> = emptyList(),
)

/**
 * Память кампании без Android: тесты подставляют карту, игра — [PrefsCampaignMemory].
 * Звёзды уровня хранятся строкой. Нет ключа — уровень ещё не пройден.
 */
interface RedBallCampaignMemory {
    fun getString(key: String, fallback: String?): String?
    fun putString(key: String, value: String)
}

class MapCampaignMemory : RedBallCampaignMemory {
    private val values = HashMap<String, String>()

    override fun getString(key: String, fallback: String?): String? = if (key in values) values[key] else fallback

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

class PrefsCampaignMemory(context: Context) : RedBallCampaignMemory {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun getString(key: String, fallback: String?): String? = prefs.getString(key, fallback)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    private companion object {
        const val PREFS = "redball_campaign"
    }
}

/**
 * Уровень 1 открыт сразу. Уровень N+1 открыт, только если пройдены все предыдущие.
 * Повторный заход оставляет лучшие звёзды и не закрывает уже открытое.
 */
class RedBallCampaignProgress(private val memory: RedBallCampaignMemory) {
    fun isCleared(level: Int): Boolean = stored(level) >= 0

    fun bestStars(level: Int): Int = stored(level).coerceAtLeast(0)

    fun isUnlocked(level: Int): Boolean {
        if (level !in 1..RedBallCampaign.COUNT) return false
        for (previous in 1 until level) if (!isCleared(previous)) return false
        return true
    }

    fun record(level: Int, stars: Int) {
        if (level !in 1..RedBallCampaign.COUNT) return
        val next = stars.coerceIn(0, 3)
        val prev = stored(level)
        if (prev >= next) return
        memory.putString(key(level), next.toString())
    }

    private fun stored(level: Int): Int {
        if (level !in 1..RedBallCampaign.COUNT) return -1
        return memory.getString(key(level), null)?.toIntOrNull() ?: -1
    }

    private fun key(level: Int) = "stars_$level"
}

/**
 * Три медали за уровень. Бонусные звёзды из секретов входят в долю,
 * а в память по-прежнему кладётся число от 0 до 3.
 */
fun medalStars(collected: Int, total: Int): Int {
    if (collected <= 0 || total <= 0) return 0
    if (total <= 3) return collected.coerceIn(0, total)
    val ratio = collected.toFloat() / total.toFloat()
    return when {
        ratio >= 0.95f -> 3
        ratio >= 0.55f -> 2
        else -> 1
    }
}

/**
 * Что происходит после флага, без Compose.
 * Табличка держится [CLEAR_HOLD_MS], кнопка «Дальше» пропускает ожидание.
 * Следующий уровень открывается и начинается с карточки вступления.
 * После десятого — финал и возврат к списку.
 */
class RedBallCampaignRun(private val progress: RedBallCampaignProgress) {
    var index: Int = 1
        private set
    var phase: Phase = Phase.INTRO
        private set
    var starsCollected: Int = 0
        private set
    var starsTotal: Int = 0
        private set
    private var markedAt: Long = 0L

    enum class Phase { INTRO, PLAY, CLEAR, FINALE, SELECT }

    fun begin(level: Int): Boolean {
        if (!progress.isUnlocked(level)) return false
        index = level
        phase = Phase.INTRO
        starsCollected = 0
        starsTotal = 0
        markedAt = 0L
        return true
    }

    fun dismissIntro() {
        if (phase == Phase.INTRO) phase = Phase.PLAY
    }

    fun reachFlag(collected: Int, total: Int, nowMs: Long) {
        if (phase == Phase.INTRO) phase = Phase.PLAY
        if (phase != Phase.PLAY) return
        progress.record(index, medalStars(collected, total))
        starsCollected = collected.coerceAtLeast(0)
        starsTotal = total.coerceAtLeast(0)
        markedAt = nowMs
        phase = if (index >= RedBallCampaign.COUNT) Phase.FINALE else Phase.CLEAR
    }

    /** Кнопка или автопоказ. false — ещё рано или флага не было. */
    fun advance(nowMs: Long, pressed: Boolean): Boolean {
        if (phase != Phase.CLEAR && phase != Phase.FINALE) return false
        if (!pressed && nowMs - markedAt < CLEAR_HOLD_MS) return false
        if (phase == Phase.FINALE) {
            phase = Phase.SELECT
            return true
        }
        val next = index + 1
        if (!progress.isUnlocked(next)) return false
        index = next
        phase = Phase.INTRO
        starsCollected = 0
        starsTotal = 0
        markedAt = 0L
        return true
    }

    companion object {
        const val CLEAR_HOLD_MS = 2000L
    }
}

/**
 * Уровни 7 и 8 рисуются главой «Водный мир».
 * Если ключа вдруг нет — остаётся классика, чтобы кампания всё равно открывалась.
 */
internal val campaignWaterSkin: RedBallSkinId =
    RedBallSkinId.entries.firstOrNull { it.storageKey == "water_world" } ?: RedBallSkinId.CLASSIC

