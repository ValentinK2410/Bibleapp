package com.example.bible.data

import android.content.Context
import android.net.Uri
import com.example.bible.data.db.MicroblogPostEntity
import com.example.bible.data.db.StudyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MicroblogRepository(private val context: Context) {

    private val dao = StudyDatabase.getInstance(context.applicationContext).microblogDao()

    suspend fun listPosts(): List<MicroblogPost> = withContext(Dispatchers.IO) {
        dao.listPosts().map { it.toDomain() }
    }

    suspend fun getPost(id: String): MicroblogPost? = withContext(Dispatchers.IO) {
        dao.getPost(id)?.toDomain()
    }

    suspend fun save(post: MicroblogPost) = withContext(Dispatchers.IO) {
        dao.upsert(
            MicroblogPostEntity(
                id = post.id,
                body = post.body,
                spansJson = spansToJson(post.spans),
                imagesJson = imageNamesToJson(post.imageFileNames),
                createdAtMs = post.createdAtMs,
                updatedAtMs = post.updatedAtMs,
            ),
        )
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val post = dao.getPost(id)
        dao.delete(id)
        val stillUsed = dao.listPosts().flatMap { imageNamesFromJson(it.imagesJson) }.toSet()
        post?.let { entity ->
            imageNamesFromJson(entity.imagesJson).forEach { name ->
                if (name !in stillUsed) {
                    File(MediaCatalogPaths.microblogDir(context), name).delete()
                }
            }
        }
    }

    suspend fun importImage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("gif") -> "gif"
                else -> "jpg"
            }
            val name = "${UUID.randomUUID()}.$ext"
            val out = File(MediaCatalogPaths.microblogDir(context), name)
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { input.copyTo(it) }
            } ?: return@withContext Result.failure(IllegalStateException("Не удалось открыть файл"))
            if (!out.exists() || out.length() == 0L) {
                out.delete()
                return@withContext Result.failure(IllegalStateException("Пустой файл"))
            }
            Result.success(name)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun MicroblogPostEntity.toDomain(): MicroblogPost = MicroblogPost(
        id = id,
        body = body,
        spans = spansFromJson(spansJson),
        imageFileNames = imageNamesFromJson(imagesJson),
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )
}
