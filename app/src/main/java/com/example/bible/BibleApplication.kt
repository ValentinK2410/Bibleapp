package com.example.bible

import android.app.Application
import com.example.bible.data.MediaCatalogMigration
import com.example.bible.data.db.StrongsImporter
import com.example.bible.data.db.StudyDatabase
import com.example.bible.data.db.StudyLegacyFilesystemMigration
import com.example.bible.data.languagestudy.LanguageStudyBootstrap
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import okhttp3.OkHttpClient
import java.util.concurrent.Executors

/**
 * Глобальный Coil: Wikimedia и часть CDN отклоняют запросы без «человеческого» User-Agent;
 * дефолтный OkHttp/Coil даёт пустые превью в сетке поиска.
 */
class BibleApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        MediaCatalogMigration.migrateIfNeeded(this)
        val app = this
        studySqliteInitExecutor.execute {
            val db = StudyDatabase.getInstance(app)
            StudyLegacyFilesystemMigration.runIfNeeded(app, db)
            StrongsImporter.importFromAssetsIfEmpty(app, db)
            LanguageStudyBootstrap.importBundledIfNeeded(app)
        }
    }

    override fun newImageLoader(): ImageLoader {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request()
                val ua = req.header("User-Agent").orEmpty()
                val next = if (ua.isBlank() || ua.startsWith("okhttp", ignoreCase = true)) {
                    req.newBuilder()
                        .header("User-Agent", USER_AGENT)
                        .build()
                } else {
                    req
                }
                chain.proceed(next)
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(okHttp)
            .components {
                add(SvgDecoder.Factory())
            }
            .build()
    }

    companion object {
        private val studySqliteInitExecutor = Executors.newSingleThreadExecutor { r ->
            Thread(r, "study-sqlite-init").apply { isDaemon = true }
        }

        private const val USER_AGENT =
            "BibleApp/1.0 (Android; offline Bible reader; image search and thumbnails)"
    }
}
