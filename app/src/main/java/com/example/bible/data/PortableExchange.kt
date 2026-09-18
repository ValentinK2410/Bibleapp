package com.example.bible.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Метаданные переносимого пакета (флешка / сервер). */
data class PortableBundleMeta(
    val bundleId: String,
    val bundleVersion: Long,
    val title: String,
    val moduleVersions: JSONObject,
)

object PortableExchange {

    const val USB_FOLDER = "BibleApp/portable"

    /** Контент для обмена между телефонами без настроек читалки и APK. */
    fun userContentShareOptions(): ShareExportOptions = ShareExportOptions(
        includeInstalledApk = false,
        appSettings = false,
        readerBookmarksHighlightsHistory = false,
        personalNotes = false,
        verseAttachmentsAndComments = false,
        semanticLexicon = false,
        wordSpanLinks = false,
        bibleCatalogImages = true,
        bibleCatalogVideos = true,
        bibleCatalogAudios = true,
        songTextsTagsAndLyricCues = true,
        songMediaFiles = true,
        userSongPlaylists = true,
        timemarkBibleProjects = true,
        studyOfflineMaterials = false,
        bibleDownloadedAudio = false,
        quranSearchHistory = false,
        userMediaPlaylists = true,
        userVideoThoughts = true,
        microblogPosts = true,
    )

    suspend fun buildModuleVersions(context: Context, preferences: BiblePreferences): JSONObject =
        withContext(Dispatchers.IO) {
            val snap = preferences.preferencesSnapshot()
            fun snapString(key: String): String {
                val entry = snap.asMap().entries.firstOrNull { it.key.name == key }?.value
                return entry as? String ?: ""
            }

            val timemarkCount = runCatching { TimemarkStore.listAllProjects(context).size }.getOrDefault(0)
            val videos = BibleUserVideo.parseList(snapString("user_bible_videos_json")).size
            val audios = BibleUserAudio.parseList(snapString("user_bible_audios_json")).size
            val images = BibleUserImage.parseList(snapString("user_bible_images_json")).size
            val songs = SongItem.parseList(snapString("user_songs_json")).size
            val songPlaylists = UserSongPlaylist.parseList(snapString("user_song_playlists_json")).size
            val mediaPlaylists = UserMediaPlaylist.parseList(snapString("user_media_playlists_json")).size
            val thoughts = VideoThought.parseMap(snapString("user_bible_video_thoughts_json")).values.sumOf { it.size }
            val microblog = runCatching {
                MicroblogRepository(context).listPosts().size
            }.getOrDefault(0)

            JSONObject().apply {
                put("timemarks", timemarkCount)
                put("videos", videos)
                put("audios", audios)
                put("images", images)
                put("songs", songs)
                put("songPlaylists", songPlaylists)
                put("mediaPlaylists", mediaPlaylists)
                put("videoThoughts", thoughts)
                put("microblog", microblog)
            }
        }

    fun buildLocalBundleMeta(title: String, moduleVersions: JSONObject): PortableBundleMeta =
        PortableBundleMeta(
            bundleId = "local_${System.currentTimeMillis()}",
            bundleVersion = System.currentTimeMillis(),
            title = title,
            moduleVersions = moduleVersions,
        )

    fun attachPortableMetaToManifest(manifest: JSONObject, meta: PortableBundleMeta) {
        manifest.put("portableExchange", true)
        manifest.put("portableBundleId", meta.bundleId)
        manifest.put("portableVersion", meta.bundleVersion)
        manifest.put("portableTitle", meta.title)
        manifest.put("moduleVersions", meta.moduleVersions)
    }

    fun readPortableMetaFromManifest(manifest: JSONObject): PortableBundleMeta? {
        if (!manifest.optBoolean("portableExchange", false)) return null
        val id = manifest.optString("portableBundleId").ifBlank { return null }
        return PortableBundleMeta(
            bundleId = id,
            bundleVersion = manifest.optLong("portableVersion", 0L),
            title = manifest.optString("portableTitle", "Пакет"),
            moduleVersions = manifest.optJSONObject("moduleVersions") ?: JSONObject(),
        )
    }

    fun formatModuleSummary(modules: JSONObject): String {
        val labels = listOf(
            "timemarks" to "таймкоды",
            "videos" to "видео",
            "audios" to "аудио",
            "images" to "картинки",
            "songs" to "песни",
            "songPlaylists" to "плейлисты песен",
            "mediaPlaylists" to "медиа-плейлисты",
            "videoThoughts" to "заметки к видео",
            "microblog" to "микроблог",
        )
        return labels.mapNotNull { (key, label) ->
            val n = modules.optInt(key, -1)
            if (n < 0) null else "$label: $n"
        }.joinToString(" · ")
    }

    fun modulesDiff(local: JSONObject, remote: JSONObject): List<String> {
        val labels = mapOf(
            "timemarks" to "таймкоды",
            "videos" to "видео",
            "audios" to "аудио",
            "images" to "картинки",
            "songs" to "песни",
            "songPlaylists" to "плейлисты песен",
            "mediaPlaylists" to "медиа-плейлисты",
            "videoThoughts" to "заметки к видео",
            "microblog" to "микроблог",
        )
        return labels.mapNotNull { (key, label) ->
            val r = remote.optInt(key, 0)
            val l = local.optInt(key, 0)
            when {
                r > l -> "$label (+${r - l} на сервере)"
                r < l -> "$label (у вас: $l, на сервере: $r)"
                r == 0 -> null
                else -> "$label ($l)"
            }
        }
    }
}
