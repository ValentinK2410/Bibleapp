package com.example.bible.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Блок своей истории: текст или картинка, в том порядке, как собрал родитель. */
data class KidsUserStoryBlock(
    val kind: String,
    val text: String = "",
    val image: String = "",
) {
    val isImage: Boolean get() = kind == IMAGE

    companion object {
        const val TEXT = "text"
        const val IMAGE = "image"
    }
}

data class KidsUserStory(
    val id: String,
    val title: String,
    val blocks: List<KidsUserStoryBlock>,
) {
    fun toBibleStory(): KidsBibleStory = KidsBibleStory(
        id = id,
        title = title,
        emoji = "📖",
        reference = "Своя история",
        gradient = listOf(0xFF7C3AED, 0xFFEC4899),
        paragraphs = blocks.filter { !it.isImage }.map { it.text },
        lesson = "",
        age = "both",
    )

    fun imageFile(context: Context, block: KidsUserStoryBlock): File? {
        if (!block.isImage || block.image.isBlank()) return null
        val file = File(context.filesDir, block.image)
        return file.takeIf { it.isFile }
    }
}

/** Истории, которые родитель собрал сам. Хранятся только на этом телефоне. */
object KidsUserStoriesStore {
    private val mutex = Mutex()
    private val flow = MutableStateFlow<List<KidsUserStory>>(emptyList())
    private var loaded = false

    fun stories(context: Context): StateFlow<List<KidsUserStory>> {
        ensureLoaded(context.applicationContext)
        return flow
    }

    fun peek(context: Context): List<KidsUserStory> {
        ensureLoaded(context.applicationContext)
        return flow.value
    }

    fun find(context: Context, id: String): KidsUserStory? = peek(context).firstOrNull { it.id == id }

    fun newId(): String = "user-" + UUID.randomUUID().toString()

    suspend fun save(context: Context, story: KidsUserStory) {
        val app = context.applicationContext
        mutex.withLock {
            ensureLoaded(app)
            val clean = story.copy(
                title = story.title.trim(),
                blocks = story.blocks.mapNotNull { block ->
                    when {
                        block.isImage && block.image.isNotBlank() && File(app.filesDir, block.image).isFile ->
                            KidsUserStoryBlock(KidsUserStoryBlock.IMAGE, image = block.image)
                        !block.isImage && block.text.isNotBlank() ->
                            KidsUserStoryBlock(KidsUserStoryBlock.TEXT, text = block.text.trim())
                        else -> null
                    }
                },
            )
            val previous = flow.value.firstOrNull { it.id == clean.id }
            val next = flow.value.filterNot { it.id == clean.id } + clean
            write(app, next)
            flow.value = next
            val stillUsed = next.flatMap { it.blocks }.map { it.image }.filter { it.isNotBlank() }.toSet()
            previous?.blocks.orEmpty().map { it.image }.filter { it.isNotBlank() && it !in stillUsed }.forEach { rel ->
                File(app.filesDir, rel).delete()
            }
        }
    }

    suspend fun delete(context: Context, id: String) {
        val app = context.applicationContext
        mutex.withLock {
            ensureLoaded(app)
            val previous = flow.value.firstOrNull { it.id == id } ?: return@withLock
            val next = flow.value.filterNot { it.id == id }
            write(app, next)
            flow.value = next
            val stillUsed = next.flatMap { it.blocks }.map { it.image }.filter { it.isNotBlank() }.toSet()
            previous.blocks.map { it.image }.filter { it.isNotBlank() && it !in stillUsed }.forEach { rel ->
                File(app.filesDir, rel).delete()
            }
            File(app.filesDir, "story_voices/$id").deleteRecursively()
        }
    }

    fun copyImage(context: Context, uri: Uri): String? =
        KidsUserMediaStorage.copyUriIntoFilesDir(context, uri, IMAGE_DIR, "jpg")

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            flow.value = read(context)
            loaded = true
        }
    }

    private fun file(context: Context) = File(context.filesDir, "kids_user_stories.json")

    private fun read(context: Context): List<KidsUserStory> {
        val f = file(context)
        if (!f.exists()) return emptyList()
        return runCatching {
            val root = JSONObject(f.readText())
            val arr = root.optJSONArray("stories") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    if (id.isBlank()) continue
                    val blocksArr = o.optJSONArray("blocks")
                    val blocks = buildList {
                        if (blocksArr != null) {
                            for (j in 0 until blocksArr.length()) {
                                val b = blocksArr.optJSONObject(j) ?: continue
                                val kind = b.optString("kind")
                                if (kind == KidsUserStoryBlock.IMAGE) {
                                    val image = b.optString("image")
                                    if (image.isNotBlank()) add(KidsUserStoryBlock(kind, image = image))
                                } else {
                                    val text = b.optString("text")
                                    if (text.isNotBlank()) add(KidsUserStoryBlock(KidsUserStoryBlock.TEXT, text = text))
                                }
                            }
                        }
                    }
                    add(KidsUserStory(id = id, title = o.optString("title"), blocks = blocks))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun write(context: Context, stories: List<KidsUserStory>) {
        val arr = JSONArray()
        stories.forEach { story ->
            arr.put(
                JSONObject().apply {
                    put("id", story.id)
                    put("title", story.title)
                    put(
                        "blocks",
                        JSONArray().apply {
                            story.blocks.forEach { block ->
                                put(
                                    JSONObject().apply {
                                        put("kind", block.kind)
                                        if (block.isImage) put("image", block.image) else put("text", block.text)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
        file(context).writeText(JSONObject().put("stories", arr).toString())
    }

    private const val IMAGE_DIR = "kids_user_story_images"
}
