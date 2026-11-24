package com.example.bible.ui

/**
 * Минимальная высота колонки текста над панелью озвучки в ландшафте.
 * Вкладка перевода (~36 dp) живёт внутри этой колонки, поэтому запас
 * больше одной строки стиха: и табы, и читаемый кусок главы остаются на экране.
 */
internal const val LANDSCAPE_READER_TEXT_RESERVE_DP = 148

/** Высота свёрнутой панели: кнопка play и строка названия, без обрезки. */
internal const val LANDSCAPE_AUDIO_BAR_COLLAPSED_DP = 56

/**
 * Потолок развёрнутой панели в ландшафте: строка транспорта (play, ползунок)
 * и одна плотная лента чипов. Дальше панель не растёт — текст остаётся сверху.
 */
internal const val LANDSCAPE_AUDIO_BAR_EXPANDED_DP = 100

/**
 * Максимальная высота панели озвучки при горизонтальной ориентации.
 *
 * [contentHeightDp] — высота области читалки под аппбаром (текст и плеер вместе).
 * 0 или отрицательное значение значит, что ограничение родителя ещё не измерено:
 * тогда возвращается предпочтительная высота, без попытки «съесть» весь экран.
 */
internal fun landscapeAudioBarMaxHeightDp(contentHeightDp: Int, collapsed: Boolean): Int {
    val preferred = if (collapsed) {
        LANDSCAPE_AUDIO_BAR_COLLAPSED_DP
    } else {
        LANDSCAPE_AUDIO_BAR_EXPANDED_DP
    }
    if (contentHeightDp <= 0) return preferred
    val room = contentHeightDp - LANDSCAPE_READER_TEXT_RESERVE_DP
    return if (room >= preferred) preferred else room.coerceIn(40, preferred)
}
