package com.example.bible.data

/** Оформление беседы «Вопрос ИИ» для буфера обмена и поста микроблога. */
object AiChatShare {

    private const val HeaderColor = 0xFF5E35B1.toInt()
    private const val UserColor = 0xFF1565C0.toInt()
    private const val UserBg = 0x1A1565C0
    private const val UserBodyColor = 0xFF37474F.toInt()
    private const val AiColor = 0xFF2E7D32.toInt()

    fun plainText(title: String, messages: List<DeepSeekMessage>): String {
        val turns = messages.filter { it.role == "user" || it.role == "assistant" }
        if (turns.isEmpty()) return ""
        return buildString {
            val head = title.trim()
            if (head.isNotEmpty()) {
                append("Беседа с ИИ\n")
                append(head)
                append("\n\n")
            } else {
                append("Беседа с ИИ\n\n")
            }
            turns.forEachIndexed { i, m ->
                if (i > 0) append('\n')
                append(if (m.role == "user") "Вы" else "ИИ")
                append('\n')
                append(m.content.trim())
                append('\n')
            }
        }.trim()
    }

    fun toMicroblogPost(title: String, messages: List<DeepSeekMessage>): MicroblogPost {
        val turns = messages.filter { it.role == "user" || it.role == "assistant" }
        val spans = mutableListOf<MicroblogSpan>()
        val body = StringBuilder()

        fun add(text: String, style: MicroblogSpan.(Int, Int) -> MicroblogSpan) {
            val start = body.length
            body.append(text)
            val end = body.length
            if (end > start) spans += MicroblogSpan(start = start, end = end).style(start, end)
        }

        add("Беседа с ИИ") { _, _ -> copy(bold = true, fontSize = 20, colorArgb = HeaderColor) }
        val head = title.trim()
        if (head.isNotEmpty()) {
            body.append('\n')
            add(head) { _, _ -> copy(italic = true, fontSize = 15, colorArgb = HeaderColor) }
        }
        body.append("\n\n")

        turns.forEachIndexed { i, m ->
            if (i > 0) body.append('\n')
            val isUser = m.role == "user"
            add(if (isUser) "Вы" else "ИИ") { _, _ ->
                if (isUser) {
                    copy(bold = true, fontSize = 14, colorArgb = UserColor, bgColorArgb = UserBg)
                } else {
                    copy(bold = true, fontSize = 14, colorArgb = AiColor)
                }
            }
            body.append('\n')
            val content = m.content.trim()
            if (content.isNotEmpty()) {
                add(content) { _, _ ->
                    if (isUser) copy(fontSize = 16, colorArgb = UserBodyColor)
                    else copy(fontSize = 16)
                }
            }
            body.append('\n')
        }

        return MicroblogPost(
            title = head.ifEmpty { "Беседа с ИИ" },
            body = body.toString().trim(),
            spans = spans,
        )
    }
}
