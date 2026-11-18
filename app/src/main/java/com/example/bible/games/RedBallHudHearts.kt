package com.example.bible.games

/**
 * Сколько полных сердец рисует HUD.
 * Потерянные не оставляют серую оболочку: слот исчезает.
 */
fun visibleHudHeartCount(hearts: Int, maxHearts: Int = RedBallSim.MAX_HEARTS): Int {
    if (maxHearts <= 0) return 0
    return hearts.coerceIn(0, maxHearts)
}

/**
 * Что показать после смены числа жизней.
 * [fading] — индексы справа, которые увеличиваются и гаснут.
 * Если жизней стало больше (перезапуск), анимации нет: рисуются все сердца.
 */
data class HudHeartLoss(
    val shown: Int,
    val fading: List<Int>,
)

fun hudHeartsAfterLoss(
    previous: Int,
    current: Int,
    maxHearts: Int = RedBallSim.MAX_HEARTS,
): HudHeartLoss {
    val prev = visibleHudHeartCount(previous, maxHearts)
    val now = visibleHudHeartCount(current, maxHearts)
    if (now >= prev) return HudHeartLoss(now, emptyList())
    return HudHeartLoss(now, (now until prev).toList())
}

/** Сколько миллисекунд потерянное сердце растёт и гаснет. */
const val HUD_HEART_FADE_MS = 250

/**
 * Поп потерянного сердца. [progress] от 0 до 1:
 * масштаб растёт, прозрачность падает до нуля.
 */
data class HudHeartPop(val scale: Float, val alpha: Float)

fun hudHeartPop(progress: Float): HudHeartPop {
    val t = progress.coerceIn(0f, 1f)
    return HudHeartPop(scale = 1f + 0.4f * t, alpha = 1f - t)
}
