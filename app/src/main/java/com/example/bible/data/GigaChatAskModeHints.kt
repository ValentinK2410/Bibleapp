package com.example.bible.data

/** Подсказки режима запроса для GigaChat (быстро / глубоко / интернет). */
object GigaChatAskModeHints {

    fun build(quick: Boolean, deep: Boolean, webSearch: Boolean): String = buildString {
        if (webSearch) {
            append(
                "Режим «Интернет»: используй встроенный поиск в интернете для актуальных данных " +
                    "(новости, цены, события, расписания). Если опираешься на источник — кратко укажи его или дату. ",
            )
        }
        when {
            quick -> append("Режим «Быстрый»: ответь кратко, 2–5 предложений, без длинного вступления.")
            deep -> append(
                "Режим «Глубоко»: ответь развёрнуто, рассуждай по шагам, приведи детали, примеры и вывод.",
            )
        }
    }.trim()

    fun timeoutMs(quick: Boolean, deep: Boolean, webSearch: Boolean): Int = when {
        webSearch -> 150_000
        deep -> 120_000
        else -> 45_000
    }
}
