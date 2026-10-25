package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class KidsChild(
    val id: String,
    val name: String,
    val age: Int,
)

data class KidsChildProgress(
    val stories: Set<String> = emptySet(),
    val points: Int = 0,
    val gameWins: Map<String, Int> = emptyMap(),
    val miniStars: Map<String, Int> = emptyMap(),
    val versesDone: Set<String> = emptySet(),
    val coloring: Set<String> = emptySet(),
    val valuesDone: Set<String> = emptySet(),
    val minutesByDay: Map<String, Int> = emptyMap(),
    val feed: List<String> = emptyList(),
    val eveningPrayedOn: String = "",
)

data class KidsFamilyState(
    val children: List<KidsChild> = emptyList(),
    val activeChildId: String = "",
    /** auto, small или big. auto следует возрасту выбранного ребёнка. */
    val ageMode: String = "auto",
    /** orthodox или western. */
    val easterCalendar: String = "orthodox",
    /** 0 — скорость по возрасту. Иначе множитель TTS. */
    val ttsRate: Float = 0f,
    val timeLimitMinutes: Int = 0,
    val eveningReminder: Boolean = false,
    val eveningHour: Int = 20,
    val progress: Map<String, KidsChildProgress> = emptyMap(),
) {
    fun activeChild(): KidsChild? = children.firstOrNull { it.id == activeChildId }

    fun bucketId(): String = activeChildId.ifBlank { KidsFamilyStore.DEFAULT_BUCKET }

    fun progressOf(id: String): KidsChildProgress = progress[id] ?: KidsChildProgress()

    fun activeProgress(): KidsChildProgress = progressOf(bucketId())

    fun resolvedMode(today: LocalDate = LocalDate.now()): KidsAgeMode {
        if (ageMode == "small") return KidsAgeMode.SMALL
        if (ageMode == "big") return KidsAgeMode.BIG
        val child = activeChild()
        return if (child != null) suggestedKidsAgeMode(child.age) else KidsAgeMode.BIG
    }

    fun todayMinutes(today: LocalDate = LocalDate.now()): Int =
        activeProgress().minutesByDay[today.toString()] ?: 0
}

object KidsFamilyStore {
    const val DEFAULT_BUCKET = "default"

    private val mutex = Mutex()
    private val flow = MutableStateFlow(KidsFamilyState())
    private var loaded = false

    fun state(context: Context): StateFlow<KidsFamilyState> {
        ensureLoaded(context.applicationContext)
        return flow
    }

    fun peek(context: Context): KidsFamilyState {
        ensureLoaded(context.applicationContext)
        return flow.value
    }

    suspend fun addChild(context: Context, name: String, age: Int): KidsChild {
        val child = KidsChild(UUID.randomUUID().toString(), name.trim(), age.coerceIn(0, 18))
        update(context) { state ->
            state.copy(children = state.children + child)
        }
        return child
    }

    suspend fun updateChild(context: Context, id: String, name: String, age: Int) {
        update(context) { state ->
            state.copy(
                children = state.children.map {
                    if (it.id == id) it.copy(name = name.trim(), age = age.coerceIn(0, 18)) else it
                },
            )
        }
    }

    suspend fun removeChild(context: Context, id: String) {
        update(context) { state ->
            val progress = state.progress.toMutableMap()
            progress.remove(id)
            state.copy(
                children = state.children.filterNot { it.id == id },
                activeChildId = if (state.activeChildId == id) "" else state.activeChildId,
                progress = progress,
            )
        }
    }

    suspend fun setActiveChild(context: Context, id: String) {
        update(context) { it.copy(activeChildId = id) }
    }

    suspend fun setAgeMode(context: Context, mode: String) {
        update(context) { it.copy(ageMode = mode) }
    }

    suspend fun setEasterCalendar(context: Context, calendar: String) {
        update(context) { it.copy(easterCalendar = calendar) }
    }

    suspend fun setTtsRate(context: Context, rate: Float) {
        update(context) { it.copy(ttsRate = rate) }
    }

    suspend fun setTimeLimit(context: Context, minutes: Int) {
        update(context) { it.copy(timeLimitMinutes = minutes.coerceAtLeast(0)) }
    }

    suspend fun setEveningReminder(context: Context, enabled: Boolean, hour: Int) {
        update(context) { it.copy(eveningReminder = enabled, eveningHour = hour.coerceIn(0, 23)) }
    }

    /** @return true, если для выбранного ребёнка история засчитана впервые. */
    suspend fun recordStory(context: Context, storyId: String): Boolean {
        val title = KidsBibleStories.byId(storyId)?.title ?: storyId
        var added = false
        update(context) { state ->
            val id = state.bucketId()
            val progress = state.progressOf(id)
            if (storyId in progress.stories) {
                state
            } else {
                added = true
                val next = progress.copy(
                    stories = progress.stories + storyId,
                    points = progress.points + 15,
                    feed = (listOf(stamp("история «$title»")) + progress.feed).take(40),
                )
                state.copy(progress = state.progress + (id to next))
            }
        }
        return added
    }

    suspend fun recordGameWin(context: Context, game: String, points: Int) {
        mutateActive(context, points = points, feed = "игра «$game»") { progress ->
            val wins = progress.gameWins.toMutableMap()
            wins[game] = (wins[game] ?: 0) + 1
            progress.copy(gameWins = wins)
        }
    }

    suspend fun addPoints(context: Context, amount: Int, feed: String? = null) {
        if (amount == 0 && feed == null) return
        mutateActive(context, points = amount, feed = feed) { it }
    }

    suspend fun recordMiniStars(context: Context, storyId: String, stars: Int) {
        val title = KidsBibleStories.byId(storyId)?.title ?: storyId
        mutateActive(context, points = 0, feed = "★★ игра после «$title» ($stars)") { progress ->
            val map = progress.miniStars.toMutableMap()
            map[storyId] = maxOf(map[storyId] ?: 0, stars.coerceIn(1, 3))
            progress.copy(miniStars = map)
        }
    }

    suspend fun recordVerse(context: Context, verseId: String) {
        mutateActive(context, points = 15, feed = "стих недели выучен") { progress ->
            progress.copy(versesDone = progress.versesDone + verseId)
        }
    }

    suspend fun recordColoring(context: Context, pageId: String) {
        mutateActive(context, points = 10, feed = "раскраска «$pageId»") { progress ->
            progress.copy(coloring = progress.coloring + pageId)
        }
    }

    suspend fun recordValue(context: Context, valueId: String, title: String) {
        mutateActive(context, points = 20, feed = "задание «$title»") { progress ->
            progress.copy(valuesDone = progress.valuesDone + valueId)
        }
    }

    suspend fun markEveningPrayer(context: Context) {
        val today = LocalDate.now().toString()
        mutateActive(context, points = 0, feed = "вечерняя молитва") { it.copy(eveningPrayedOn = today) }
    }

    suspend fun addMinutes(context: Context, minutes: Int) {
        if (minutes <= 0) return
        val day = LocalDate.now().toString()
        mutateActive(context, points = 0, feed = null) { progress ->
            val map = progress.minutesByDay.toMutableMap()
            map[day] = (map[day] ?: 0) + minutes
            progress.copy(minutesByDay = map)
        }
    }

    suspend fun resetProgress(context: Context, childId: String) {
        update(context) { state ->
            val progress = state.progress.toMutableMap()
            progress[childId] = KidsChildProgress()
            state.copy(progress = progress)
        }
    }

    private suspend fun mutateActive(
        context: Context,
        points: Int,
        feed: String?,
        transform: (KidsChildProgress) -> KidsChildProgress,
    ) {
        update(context) { state ->
            val id = state.bucketId()
            var progress = transform(state.progressOf(id))
            if (points != 0) progress = progress.copy(points = (progress.points + points).coerceAtLeast(0))
            if (feed != null) {
                val line = stamp(feed)
                progress = progress.copy(feed = (listOf(line) + progress.feed).take(40))
            }
            val map = state.progress.toMutableMap()
            map[id] = progress
            state.copy(progress = map)
        }
    }

    private suspend fun update(context: Context, transform: (KidsFamilyState) -> KidsFamilyState) {
        val app = context.applicationContext
        mutex.withLock {
            ensureLoaded(app)
            val next = transform(flow.value)
            write(app, next)
            flow.value = next
        }
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            flow.value = read(context)
            loaded = true
        }
    }

    private fun file(context: Context) = File(context.filesDir, "kids_family.json")

    private fun read(context: Context): KidsFamilyState {
        val f = file(context)
        if (!f.exists()) return KidsFamilyState()
        return runCatching { decode(JSONObject(f.readText())) }.getOrDefault(KidsFamilyState())
    }

    private fun write(context: Context, state: KidsFamilyState) {
        file(context).writeText(encode(state).toString())
    }

    private fun stamp(line: String): String {
        val day = LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM", Locale("ru")))
        return "$day · $line"
    }
}

private fun encode(state: KidsFamilyState): JSONObject = JSONObject().apply {
    put("activeChildId", state.activeChildId)
    put("ageMode", state.ageMode)
    put("easterCalendar", state.easterCalendar)
    put("ttsRate", state.ttsRate.toDouble())
    put("timeLimitMinutes", state.timeLimitMinutes)
    put("eveningReminder", state.eveningReminder)
    put("eveningHour", state.eveningHour)
    put("children", JSONArray().apply {
        state.children.forEach { child ->
            put(JSONObject().put("id", child.id).put("name", child.name).put("age", child.age))
        }
    })
    put("progress", JSONObject().apply {
        state.progress.forEach { (id, progress) -> put(id, encodeProgress(progress)) }
    })
}

private fun encodeProgress(progress: KidsChildProgress): JSONObject = JSONObject().apply {
    put("stories", JSONArray(progress.stories.toList()))
    put("points", progress.points)
    put("gameWins", JSONObject(progress.gameWins))
    put("miniStars", JSONObject(progress.miniStars))
    put("versesDone", JSONArray(progress.versesDone.toList()))
    put("coloring", JSONArray(progress.coloring.toList()))
    put("valuesDone", JSONArray(progress.valuesDone.toList()))
    put("minutesByDay", JSONObject(progress.minutesByDay))
    put("feed", JSONArray(progress.feed))
    put("eveningPrayedOn", progress.eveningPrayedOn)
}

private fun decode(json: JSONObject): KidsFamilyState {
    val children = buildList {
        val arr = json.optJSONArray("children") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            add(KidsChild(o.optString("id").ifBlank { UUID.randomUUID().toString() }, name, o.optInt("age")))
        }
    }
    val progress = mutableMapOf<String, KidsChildProgress>()
    val progressJson = json.optJSONObject("progress") ?: JSONObject()
    val keys = progressJson.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        progress[key] = decodeProgress(progressJson.optJSONObject(key) ?: JSONObject())
    }
    return KidsFamilyState(
        children = children,
        activeChildId = json.optString("activeChildId"),
        ageMode = json.optString("ageMode", "auto").ifBlank { "auto" },
        easterCalendar = json.optString("easterCalendar", "orthodox"),
        ttsRate = json.optDouble("ttsRate", 0.0).toFloat(),
        timeLimitMinutes = json.optInt("timeLimitMinutes"),
        eveningReminder = json.optBoolean("eveningReminder"),
        eveningHour = json.optInt("eveningHour", 20),
        progress = progress,
    )
}

private fun decodeProgress(json: JSONObject) = KidsChildProgress(
    stories = json.optJSONArray("stories").toStringSet(),
    points = json.optInt("points"),
    gameWins = json.optJSONObject("gameWins").toIntMap(),
    miniStars = json.optJSONObject("miniStars").toIntMap(),
    versesDone = json.optJSONArray("versesDone").toStringSet(),
    coloring = json.optJSONArray("coloring").toStringSet(),
    valuesDone = json.optJSONArray("valuesDone").toStringSet(),
    minutesByDay = json.optJSONObject("minutesByDay").toIntMap(),
    feed = json.optJSONArray("feed").toStringList(),
    eveningPrayedOn = json.optString("eveningPrayedOn"),
)

private fun JSONArray?.toStringSet(): Set<String> {
    if (this == null) return emptySet()
    return buildSet {
        for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
    }
}

private fun JSONObject?.toIntMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    val out = mutableMapOf<String, Int>()
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        out[key] = optInt(key)
    }
    return out
}
