package com.example.bible.data

import android.content.Context
import com.example.bible.data.db.AiChatEntity
import com.example.bible.data.db.AiChatMessageEntity
import com.example.bible.data.db.StudyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AiChatSummary(
    val id: Long,
    val title: String,
    val updatedAtMs: Long,
)

/**
 * Чаты «Вопрос ИИ» хранятся в [StudyDatabase].
 * В API уходит только короткий хвост диалога, а не весь архив.
 */
class AiChatRepository(
    context: Context,
    private val provider: String = PROVIDER_DEEPSEEK,
) {

    private val appContext = context.applicationContext
    private val dao = StudyDatabase.getInstance(appContext).aiChatDao()

    suspend fun listSummaries(): List<AiChatSummary> = withContext(Dispatchers.IO) {
        dao.listChats(provider).map { AiChatSummary(it.id, it.title, it.updatedAtMs) }
    }

    suspend fun listMessages(chatId: Long): List<AiChatMessageEntity> = withContext(Dispatchers.IO) {
        dao.listMessages(chatId)
    }

    suspend fun getSummary(id: Long): AiChatSummary? = withContext(Dispatchers.IO) {
        dao.getChat(id)?.let { AiChatSummary(it.id, it.title, it.updatedAtMs) }
    }

    suspend fun createChat(firstQuestion: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insertChat(
            AiChatEntity(
                title = titleFromQuestion(firstQuestion),
                createdAtMs = now,
                updatedAtMs = now,
                provider = provider,
            ),
        )
    }

    suspend fun addMessage(chatId: Long, role: String, content: String): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = dao.insertMessage(
            AiChatMessageEntity(
                chatId = chatId,
                role = role,
                content = content,
                createdAtMs = now,
            ),
        )
        dao.touchChat(chatId, now)
        id
    }

    suspend fun updateMessage(id: Long, content: String) = withContext(Dispatchers.IO) {
        dao.updateMessage(id, content)
    }

    suspend fun renameChat(id: Long, title: String) = withContext(Dispatchers.IO) {
        dao.updateChat(id, title, System.currentTimeMillis())
    }

    suspend fun deleteMessage(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteMessage(id)
    }

    suspend fun deleteChat(id: Long) = withContext(Dispatchers.IO) {
        val messages = dao.listMessages(id)
        dao.deleteChat(id)
        if (provider == PROVIDER_GIGACHAT) {
            val dir = GigaChatImages.dir(appContext)
            messages.forEach { msg ->
                GigaChatImages.referencedFiles(msg.content, dir).forEach { file ->
                    runCatching { file.delete() }
                }
            }
        }
    }

    companion object {
        const val PROVIDER_DEEPSEEK = "deepseek"
        const val PROVIDER_GIGACHAT = "gigachat"

        const val SYSTEM_PROMPT =
            "Ты помощник в приложении для чтения Библии. Отвечай по-русски ясно и по существу. " +
                "Если вопрос о Писании — не выдумывай цитаты. На другие темы тоже помогай. " +
                "Тебе дан только недавний фрагмент диалога; не опирайся на более ранние реплики, которых здесь нет."

        private const val MAX_API_MESSAGES = 12
        private const val MAX_API_CHARS = 8_000
        private const val MAX_ONE_MESSAGE_CHARS = 3_000

        fun titleFromQuestion(question: String): String {
            val one = question.trim().replace(Regex("\\s+"), " ")
            if (one.isEmpty()) return "Новый чат"
            return if (one.length <= 48) one else one.take(47) + "…"
        }

        fun apiMessages(stored: List<AiChatMessageEntity>): List<DeepSeekMessage> {
            val turns = stored.filter { it.role == "user" || it.role == "assistant" }
            val picked = ArrayDeque<DeepSeekMessage>()
            var chars = 0
            for (item in turns.asReversed()) {
                if (picked.size >= MAX_API_MESSAGES) break
                val text = GigaChatImages.stripForApi(item.content).take(MAX_ONE_MESSAGE_CHARS)
                if (picked.isNotEmpty() && chars + text.length > MAX_API_CHARS) break
                picked.addFirst(DeepSeekMessage(item.role, text))
                chars += text.length
            }
            return listOf(DeepSeekMessage("system", SYSTEM_PROMPT)) + picked
        }
    }
}
