package com.example.bible.deeplink

import com.example.bible.data.AppSectionUsage
import com.example.bible.data.BibleCanon
import com.example.bible.data.KidsPicturedRoutes
import com.example.bible.data.TranslationId
import com.example.bible.ui.theme.BibleAppThemePreset
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Разбор и сборка ссылок, которые открывают приложение.
 *
 * Своя схема `bible://` работает сразу. Форма `https://` — Android App Links:
 * хост по умолчанию `bible.example.com` (заглушка, её же пишет `BuildConfig.DEEP_LINK_HOST`
 * и placeholder `deepLinkHost` в манифесте). Пока на домене нет
 * `https://<хост>/.well-known/assetlinks.json` с пакетом `com.example.bible.sqlite`
 * и SHA-256 сертификата подписи, система не отдаёт https-ссылку приложению
 * (её откроет браузер). Схема `bible://` проверки домена не требует.
 *
 * Неизвестная ссылка нашей схемы или нашего хоста не роняет приложение:
 * [parse] возвращает домашний экран книг.
 *
 * Стиль — это пресет оформления [BibleAppThemePreset] (`standard`, `papyrus`,
 * `starfield`, …). Отдельная ссылка `/style/{ключ}` включает пресет и открывает
 * мастерскую темы. Параметр `?style=` на ссылке стиха, главы или раздела
 * включает тот же пресет и открывает эту цель.
 */
object BibleDeepLinks {
    const val SCHEME = "bible"

    /** Держите в синхроне с `deepLinkHost` в `app/build.gradle.kts`. */
    const val DEFAULT_HTTPS_HOST = "bible.example.com"

    private const val MAX_VERSE = 200

    /** Экраны NavHost без обязательных аргументов в пути. Неизвестный маршрут сюда не попадёт. */
    private val NAV_ROUTES: Set<String> = setOf(
        "books",
        "azbuka",
        "cifry",
        "kids",
        "kids_stories",
        "kids_bible",
        "kids_prayers",
        "kids_verse",
        "kids_songs",
        "kids_coloring",
        "kids_heroes",
        "kids_map",
        "kids_values",
        "kids_parents",
        "kids_games",
        "kids_adameve",
        "kids_moses",
        "kids_dino",
        "kids_redball",
        "kids_brainring",
        "kids_tictactoe",
        "kids_checkers",
        "kids_go",
        "kids_pipes",
        "kids_edit_sections",
        "experiment",
        "experiment_camera",
        "experiment_camera_control",
        "experiment_camera_4",
        "experiment_camera_5_mediapipe",
        "experiment_calls_sms",
        "experiment_sms_inbox",
        "experiment_sms_reactions",
        "experiment_sms_speech_overrides",
        "experiment_sensor_lab",
        "experiment_sound_lab",
        "experiment_wifi",
        "experiment_auto",
        "kids_colors",
        "kids_seasons",
        "kids_countries",
        "kids_animals",
        "kids_fish",
        "kids_snakes",
        "kids_insects",
        "kids_trees",
        "kids_plants",
        "songs",
        "song_lists",
        "songs_pv",
        "video_download",
        "reading_plan",
        "network_region",
        "theme_studio",
        "main_settings",
        "about_app",
        "ai",
        "ai_settings",
        "ai_ask",
        "ai_photo_identify",
        "ai_photo_transcribe",
        "gigachat",
        "gigachat_settings",
        "gigachat_ask",
        "gigachat_photo_identify",
        "gigachat_photo_transcribe",
        "deepseek_camera",
        "books_menu_order",
        "backup",
        "share_app",
        "offline_download",
        "maps",
        "my_travels",
        "my_church",
        "church_participants",
        "church_accounting",
        "church_orders",
        "church_protocols",
        "church_certificates",
        "house_care",
        "house_family_menu",
        "house_cleaning",
        "house_clean_settings",
        "house_clean_stats",
        "media",
        "media_microblog",
        "media_musician",
        "media_musician_studio",
        "media_musician_notes",
        "media_musician_metronome",
        "media_musician_guitar",
        "media_musician_violin",
        "media_pictures",
        "media_videos",
        "media_video_playlists",
        "media_audios",
        "media_audio_playlists",
        "audio_download",
        "genealogy",
        "other_books",
        "gita",
        "gita_search",
        "quran",
        "quran_search",
        "strongs",
        "search",
        "bookmarks",
        "bible_coverage",
        "stats",
        "history",
        "semantic_lexicon",
        "language_study",
        "notes",
        "daily_journal",
        "app_contacts",
        "dual",
        "timemark_editor",
    )

    private val KINDS = setOf("verse", "chapter", "section", "style", "home", "books")

    fun isAppLink(raw: String?, httpsHost: String = DEFAULT_HTTPS_HOST): Boolean {
        val parts = raw?.trim()?.takeIf { it.isNotEmpty() }?.let(::splitLink) ?: return false
        if (parts.scheme == SCHEME) return true
        if (parts.scheme != "https" && parts.scheme != "http") return false
        return parts.host.equals(httpsHost, ignoreCase = true)
    }

    /**
     * Разбор ссылки. Для строки, которая не является ссылкой приложения, тоже
     * возвращает домашний экран — вызывающий код сам решает, ссылка ли это ([isAppLink]).
     */
    fun parse(raw: String?, httpsHost: String = DEFAULT_HTTPS_HOST): Target {
        val parts = raw?.trim()?.takeIf { it.isNotEmpty() }?.let(::splitLink) ?: return Target.HOME
        if (!isAppLink(raw, httpsHost)) return Target.HOME
        val style = canonicalStyle(parts.query["style"])
        val segments = logicalSegments(parts, httpsHost)
        if (segments.isEmpty()) return Target(route = "books", translationCode = null, styleKey = style)
        val kind = segments[0].lowercase()
        val rest = segments.drop(1)
        return when (kind) {
            "verse" -> parseVerse(rest, style) ?: Target(route = "books", translationCode = null, styleKey = style)
            "chapter" -> parseChapter(rest, style) ?: Target(route = "books", translationCode = null, styleKey = style)
            "section" -> {
                val route = rest.firstOrNull()?.let(::sectionRoute)
                if (route == null) {
                    Target(route = "books", translationCode = null, styleKey = style)
                } else {
                    Target(route = route, translationCode = null, styleKey = style)
                }
            }
            "style" -> {
                val key = canonicalStyle(rest.firstOrNull()) ?: style
                if (key == null) Target.HOME else Target(route = "theme_studio", translationCode = null, styleKey = key)
            }
            "home", "books" -> Target(route = "books", translationCode = null, styleKey = style)
            else -> Target(route = "books", translationCode = null, styleKey = style)
        }
    }

    fun requestFrom(
        dataString: String?,
        startRouteExtra: String?,
        httpsHost: String = DEFAULT_HTTPS_HOST,
        nonce: Long = System.nanoTime(),
    ): DeepLinkRequest? {
        if (isAppLink(dataString, httpsHost)) {
            val target = parse(dataString, httpsHost)
            return DeepLinkRequest(
                nonce = nonce,
                route = target.route,
                translationCode = target.translationCode,
                styleKey = target.styleKey,
            )
        }
        val extra = startRouteExtra?.trim().orEmpty()
        if (extra.isEmpty()) return null
        return DeepLinkRequest(
            nonce = nonce,
            route = extra,
            translationCode = null,
            styleKey = null,
        )
    }

    fun verseUrl(
        translation: TranslationId,
        bookId: String,
        chapter: Int,
        verse: Int,
        styleKey: String? = null,
        https: Boolean = true,
        httpsHost: String = DEFAULT_HTTPS_HOST,
    ): String = link(
        path = "verse/${translation.code}/${bookId.trim().lowercase()}/$chapter/$verse",
        styleKey = styleKey,
        https = https,
        httpsHost = httpsHost,
    )

    fun chapterUrl(
        translation: TranslationId,
        bookId: String,
        chapter: Int,
        styleKey: String? = null,
        https: Boolean = true,
        httpsHost: String = DEFAULT_HTTPS_HOST,
    ): String = link(
        path = "chapter/${translation.code}/${bookId.trim().lowercase()}/$chapter",
        styleKey = styleKey,
        https = https,
        httpsHost = httpsHost,
    )

    fun sectionUrl(
        section: String,
        styleKey: String? = null,
        https: Boolean = true,
        httpsHost: String = DEFAULT_HTTPS_HOST,
    ): String = link(
        path = "section/${section.trim()}",
        styleKey = styleKey,
        https = https,
        httpsHost = httpsHost,
    )

    fun styleUrl(
        styleKey: String,
        https: Boolean = true,
        httpsHost: String = DEFAULT_HTTPS_HOST,
    ): String = link(
        path = "style/${canonicalStyle(styleKey) ?: styleKey.trim()}",
        styleKey = null,
        https = https,
        httpsHost = httpsHost,
    )

    /** Текст «поделиться стихом» плюс https-ссылка и ссылка своей схемы. */
    fun appendToShareText(
        body: String,
        translation: TranslationId,
        bookId: String,
        chapter: Int,
        verse: Int,
        httpsHost: String = DEFAULT_HTTPS_HOST,
    ): String {
        val https = verseUrl(
            translation = translation,
            bookId = bookId,
            chapter = chapter,
            verse = verse,
            https = true,
            httpsHost = httpsHost,
        )
        val custom = verseUrl(
            translation = translation,
            bookId = bookId,
            chapter = chapter,
            verse = verse,
            https = false,
            httpsHost = httpsHost,
        )
        val trimmed = body.trimEnd()
        return if (trimmed.isEmpty()) "$https\n$custom" else "$trimmed\n\n$https\n$custom"
    }

    private fun link(path: String, styleKey: String?, https: Boolean, httpsHost: String): String {
        val style = canonicalStyle(styleKey)
        val query = if (style != null) "?style=$style" else ""
        return if (https) "https://$httpsHost/$path$query" else "$SCHEME://$path$query"
    }

    private fun parseVerse(parts: List<String>, style: String?): Target? {
        val located = locatePassage(parts, withVerse = true) ?: return null
        return Target(
            route = "read/${located.book.id}/${located.chapter}/${located.verse}",
            translationCode = located.translationCode,
            styleKey = style,
        )
    }

    private fun parseChapter(parts: List<String>, style: String?): Target? {
        val located = locatePassage(parts, withVerse = false) ?: return null
        return Target(
            route = "read/${located.book.id}/${located.chapter}/1",
            translationCode = located.translationCode,
            styleKey = style,
        )
    }

    private data class Located(
        val translationCode: String?,
        val book: com.example.bible.data.CanonBookEntry,
        val chapter: Int,
        val verse: Int,
    )

    /**
     * Стих: `{перевод}/{книга}/{глава}/{стих}` или `{книга}/{глава}/{стих}`.
     * Глава: `{перевод}/{книга}/{глава}` или `{книга}/{глава}`.
     * Без перевода приложение оставляет уже выбранный перевод.
     */
    private fun locatePassage(parts: List<String>, withVerse: Boolean): Located? {
        if (parts.isEmpty()) return null
        val translation = resolveTranslation(parts[0])
        val bookIndex: Int
        val translationCode: String?
        if (translation != null && parts.size == (if (withVerse) 4 else 3)) {
            translationCode = translation.code
            bookIndex = 1
        } else if (translation == null && parts.size == (if (withVerse) 3 else 2)) {
            translationCode = null
            bookIndex = 0
        } else {
            return null
        }
        val book = resolveBook(parts[bookIndex]) ?: return null
        val chapter = parts.getOrNull(bookIndex + 1)?.toIntOrNull() ?: return null
        if (chapter !in 1..book.chapters) return null
        val verse = if (withVerse) {
            val raw = parts.getOrNull(bookIndex + 2)?.toIntOrNull() ?: return null
            if (raw !in 1..MAX_VERSE) return null
            raw
        } else {
            1
        }
        return Located(translationCode, book, chapter, verse)
    }

    private fun resolveTranslation(raw: String): TranslationId? =
        TranslationId.entries.firstOrNull { it.code.equals(raw.trim(), ignoreCase = true) }

    private fun resolveBook(raw: String): com.example.bible.data.CanonBookEntry? {
        val trimmed = decode(raw).trim()
        if (trimmed.isEmpty()) return null
        BibleCanon.byId(trimmed.lowercase())?.let { return it }
        return BibleCanon.allBooks.find { book ->
            book.nameEn.equals(trimmed, ignoreCase = true) ||
                book.nameRu.equals(trimmed, ignoreCase = true) ||
                book.abbrRu.equals(trimmed, ignoreCase = true)
        }
    }

    private fun sectionRoute(token: String): String? {
        val key = decode(token).trim()
        if (key.isEmpty()) return null
        val lowered = key.lowercase()
        if (lowered == "home") return "books"
        AppSectionUsage.catalog.firstOrNull { section ->
            section.id.equals(key, ignoreCase = true) ||
                section.route?.equals(key, ignoreCase = true) == true
        }?.let { section ->
            mappedRoute(section.route)?.let { return it }
        }
        mappedRoute(lowered)?.let { return it }
        return null
    }

    private fun mappedRoute(route: String?): String? {
        val head = route?.trim()?.substringBefore("?")?.substringBefore("/")?.lowercase().orEmpty()
        if (head.isEmpty()) return null
        if (head in NAV_ROUTES) return head
        if (head in KidsPicturedRoutes.all) return "kids_album?r=$head"
        return null
    }

    private fun canonicalStyle(raw: String?): String? {
        val key = raw?.trim().orEmpty()
        if (key.isEmpty()) return null
        return BibleAppThemePreset.entries
            .firstOrNull { it.storageKey.equals(key, ignoreCase = true) }
            ?.storageKey
    }

    private fun logicalSegments(parts: LinkParts, httpsHost: String): List<String> {
        val host = parts.host.lowercase()
        val hostIsKind = parts.scheme == SCHEME &&
            host in KINDS &&
            !host.equals(httpsHost, ignoreCase = true)
        return if (hostIsKind) listOf(host) + parts.pathSegments else parts.pathSegments
    }

    private data class LinkParts(
        val scheme: String,
        val host: String,
        val pathSegments: List<String>,
        val query: Map<String, String>,
    )

    private fun splitLink(raw: String): LinkParts? {
        val trimmed = raw.trim()
        val schemeEnd = trimmed.indexOf(':')
        if (schemeEnd <= 0) return null
        val scheme = trimmed.substring(0, schemeEnd).lowercase()
        var rest = trimmed.substring(schemeEnd + 1)
        if (rest.startsWith("//")) rest = rest.substring(2)
        val hash = rest.indexOf('#')
        if (hash >= 0) rest = rest.substring(0, hash)
        val queryStart = rest.indexOf('?')
        val queryRaw = if (queryStart >= 0) rest.substring(queryStart + 1) else ""
        if (queryStart >= 0) rest = rest.substring(0, queryStart)
        val slash = rest.indexOf('/')
        val host = if (slash < 0) rest else rest.substring(0, slash)
        val path = if (slash < 0) "" else rest.substring(slash + 1)
        val segments = path.split('/').filter { it.isNotEmpty() }.map(::decode)
        return LinkParts(
            scheme = scheme,
            host = decode(host),
            pathSegments = segments,
            query = parseQuery(queryRaw),
        )
    }

    private fun parseQuery(raw: String): Map<String, String> {
        if (raw.isBlank()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (part in raw.split('&')) {
            if (part.isEmpty()) continue
            val eq = part.indexOf('=')
            val name = if (eq < 0) part else part.substring(0, eq)
            val value = if (eq < 0) "" else part.substring(eq + 1)
            out[decode(name)] = decode(value)
        }
        return out
    }

    private fun decode(raw: String): String =
        try {
            URLDecoder.decode(raw, StandardCharsets.UTF_8.name())
        } catch (_: IllegalArgumentException) {
            raw
        }
}

data class DeepLinkRequest(
    val nonce: Long,
    val route: String,
    val translationCode: String?,
    val styleKey: String?,
)

data class Target(
    val route: String,
    val translationCode: String?,
    val styleKey: String?,
) {
    companion object {
        val HOME = Target(route = "books", translationCode = null, styleKey = null)
    }
}
