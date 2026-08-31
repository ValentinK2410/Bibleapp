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
                title = post.title.trim(),
                body = post.body,
                spansJson = spansToJson(post.spans),
                imagesJson = imagesToJson(post.images),
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

    suspend fun cropImage(
        fileName: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        outputScale: Float,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val dir = MediaCatalogPaths.microblogDir(context)
            val src = File(dir, fileName)
            if (!src.isFile) return@withContext Result.failure(IllegalStateException("Файл не найден"))
            val bitmap = MicroblogImageOps.loadBitmap(src)
                ?: return@withContext Result.failure(IllegalStateException("Не удалось прочитать фото"))
            val cropped = try {
                MicroblogImageOps.cropAndScale(bitmap, left, top, right, bottom, outputScale)
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            val outName = "${UUID.randomUUID()}.jpg"
            val out = File(dir, outName)
            try {
                MicroblogImageOps.saveJpeg(cropped, out)
            } finally {
                if (!cropped.isRecycled) cropped.recycle()
            }
            if (!out.exists() || out.length() == 0L) {
                out.delete()
                return@withContext Result.failure(IllegalStateException("Не удалось сохранить кадр"))
            }
            Result.success(outName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteUnusedImage(fileName: String, stillReferenced: Collection<String>) = withContext(Dispatchers.IO) {
        if (fileName.isBlank() || fileName in stillReferenced) return@withContext
        File(MediaCatalogPaths.microblogDir(context), fileName).delete()
    }

    private fun MicroblogPostEntity.toDomain(): MicroblogPost = MicroblogPost(
        id = id,
        title = title,
        body = body,
        spans = spansFromJson(spansJson),
        images = imagesFromJson(imagesJson),
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
    )
}
