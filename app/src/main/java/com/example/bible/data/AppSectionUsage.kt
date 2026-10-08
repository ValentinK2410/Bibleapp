package com.example.bible.data

import org.json.JSONObject

/**
 * Сколько раз открывали каждый раздел приложения.
 * Нулевые разделы тоже остаются в списке — так видно, чем почти не пользуются.
 */
object AppSectionUsage {
    data class Section(
        val id: String,
        val title: String,
        val area: String,
        val route: String? = null,
    )

    data class Row(
        val section: Section,
        val opens: Int,
        val lastAt: Long,
    )

    const val BIBLE_AREA = "Библия"

    enum class Band(val label: String) {
        OFTEN("часто"),
        SOMETIMES("иногда"),
        RARE("редко"),
        NEVER("не открывали"),
    }

    val catalog: List<Section> = listOf(
        Section("reading", "Чтение Библии", "Библия", "books"),
        Section(BooksMainMenuOrder.SEARCH, "Поиск", "Библия", "search"),
        Section(BooksMainMenuOrder.BOOKMARKS, "Закладки", "Библия", "bookmarks"),
        Section(BooksMainMenuOrder.HISTORY, "История чтения", "Библия", "history"),
        Section(BooksMainMenuOrder.COVERAGE, "Статистика", "Библия", "stats"),
        Section(BooksMainMenuOrder.NOTES, "Заметки", "Библия", "notes"),
        Section(BooksMainMenuOrder.DAILY_JOURNAL, "Ежедневник", "Библия", "daily_journal"),
        Section(BooksMainMenuOrder.DUAL, "Сравнение переводов", "Библия", "dual"),
        Section(BooksMainMenuOrder.READING_PLAN, "План чтения", "Библия", "reading_plan"),
        Section(BooksMainMenuOrder.TIMEMARK, "Таймкоды", "Библия", "timemark_editor"),
        Section(BooksMainMenuOrder.STRONGS, "Словарь Стронга", "Библия", "strongs"),
        Section(BooksMainMenuOrder.SEMANTIC_LEXICON, "Тематическая подсветка", "Библия", "semantic_lexicon"),
        Section(BooksMainMenuOrder.LANGUAGE_STUDY, "Изучение языков", "Библия", "language_study"),
        Section(BooksMainMenuOrder.NARRATOR, "Озвучка главы", "Библия", null),
        Section(BooksMainMenuOrder.TEXT_SIZE, "Размер текста", "Библия", null),
        Section(BooksMainMenuOrder.MEDIA, "Медиа", "Медиа", "media"),
        Section("media_pictures", "Картинки", "Медиа", "media_pictures"),
        Section("media_videos", "Видео", "Медиа", "media_videos"),
        Section("media_audios", "Аудио", "Медиа", "media_audios"),
        Section("songs", "Песни", "Медиа", "songs"),
        Section("media_musician", "Музыканту", "Медиа", "media_musician"),
        Section("media_microblog", "Микроблог", "Медиа", "media_microblog"),
        Section("downloads", "Загрузки", "Медиа", "video_download"),
        Section(BooksMainMenuOrder.MAPS, "Карты", "Жизнь", "maps"),
        Section(BooksMainMenuOrder.TRAVEL, "Путешествия", "Жизнь", "my_travels"),
        Section(BooksMainMenuOrder.GENEALOGY, "Родословная", "Жизнь", "genealogy"),
        Section(BooksMainMenuOrder.OTHER_BOOKS, "Другие книги", "Жизнь", "other_books"),
        Section(BooksMainMenuOrder.CONTACTS, "Контакты", "Жизнь", "app_contacts"),
        Section("quran", "Коран", "Жизнь", "quran"),
        Section(BooksMainMenuOrder.MY_CHURCH, "Моя церковь", "Церковь", "my_church"),
        Section("church_participants", "Участники", "Церковь", "church_participants"),
        Section("church_accounting", "Учёт", "Церковь", "church_accounting"),
        Section("church_orders", "Приказы", "Церковь", "church_orders"),
        Section("church_protocols", "Протоколы", "Церковь", "church_protocols"),
        Section("church_certificates", "Свидетельства", "Церковь", "church_certificates"),
        Section(BooksMainMenuOrder.AI, "ИИ", "ИИ", "ai"),
        Section(BooksMainMenuOrder.GIGACHAT, "GigaChat", "ИИ", "gigachat"),
        Section(BooksMainMenuOrder.KIDS, "Детям", "Детям", "kids"),
        Section("azbuka", "Азбука", "Детям", "azbuka"),
        Section("cifry", "Цифры", "Детям", "cifry"),
        Section("kids_games", "Игры", "Детям", "kids_games"),
        Section("kids_colors", "Цвета", "Детям", "kids_colors"),
        Section("kids_seasons", "Времена года", "Детям", "kids_seasons"),
        Section("kids_countries", "Страны", "Детям", "kids_countries"),
        Section("kids_animals", "Животные", "Детям", "kids_animals"),
        Section("kids_fish", "Рыбы", "Детям", "kids_fish"),
        Section("kids_snakes", "Змеи", "Детям", "kids_snakes"),
        Section("kids_insects", "Насекомые", "Детям", "kids_insects"),
        Section("kids_trees", "Деревья", "Детям", "kids_trees"),
        Section("kids_plants", "Растения", "Детям", "kids_plants"),
        Section(BooksMainMenuOrder.EXPERIMENT, "Эксперимент", "Эксперимент", "experiment"),
        Section("experiment_camera", "Камера", "Эксперимент", "experiment_camera"),
        Section("experiment_calls_sms", "Звонки и SMS", "Эксперимент", "experiment_calls_sms"),
        Section("experiment_sensor_lab", "Датчики", "Эксперимент", "experiment_sensor_lab"),
        Section("experiment_sound_lab", "Звук", "Эксперимент", "experiment_sound_lab"),
        Section("experiment_wifi", "Wi‑Fi", "Эксперимент", "experiment_wifi"),
        Section("experiment_auto", "Авто", "Эксперимент", "experiment_auto"),
        Section("main_settings", "Настройки", "Настройки", "main_settings"),
        Section(BooksMainMenuOrder.THEME, "Тема", "Настройки", "theme_studio"),
        Section(BooksMainMenuOrder.NETWORK_REGION, "Сеть и регион", "Настройки", "network_region"),
        Section("backup", "Резервная копия", "Настройки", "backup"),
        Section("share_app", "Поделиться приложением", "Настройки", "share_app"),
        Section("offline_download", "Офлайн-загрузка", "Настройки", "offline_download"),
    )

    fun sectionIdForRoute(route: String?): String? {
        if (route.isNullOrBlank()) return null
        val path = route.substringBefore("?")
        val head = path.substringBefore("/")
        return when {
            head == "read" || head == "chapters" || head == "verses" -> "reading"
            head == "bible_coverage" || head == "stats" || head == "stats_detail" -> BooksMainMenuOrder.COVERAGE
            head == "app_contacts" -> BooksMainMenuOrder.CONTACTS
            head == "my_travels" -> BooksMainMenuOrder.TRAVEL
            head == "note_edit" -> BooksMainMenuOrder.NOTES
            head == "timemark_editor" -> BooksMainMenuOrder.TIMEMARK
            head == "song_lists" || head == "songs_pv" -> "songs"
            head == "video_download" || head == "audio_download" -> "downloads"
            head == "media_video_playlists" -> "media_videos"
            head == "media_audio_playlists" -> "media_audios"
            head == "kids_tictactoe" || head == "kids_checkers" || head == "kids_go" || head == "kids_pipes" -> "kids_games"
            head == "experiment_camera_control" ||
                head == "experiment_camera_4" ||
                head == "experiment_camera_5_mediapipe" -> "experiment_camera"
            head == "experiment_sms_inbox" ||
                head == "experiment_sms_reactions" ||
                head == "experiment_sms_speech_overrides" -> "experiment_calls_sms"
            head == "quran_search" || head == "quran_arabic_sandbox" -> "quran"
            head == "theme_studio" -> BooksMainMenuOrder.THEME
            head == "books" || head == "books_menu_order" || head == "about_app" -> null
            catalog.any { it.id == head } -> head
            else -> null
        }
    }

    fun routeFor(id: String): String? = catalog.find { it.id == id }?.route

    fun parse(json: String): Map<String, Pair<Int, Long>> {
        if (json.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(json)
            buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val id = keys.next()
                    val row = obj.optJSONObject(id) ?: continue
                    val opens = row.optInt("n", 0)
                    if (opens > 0) put(id, opens to row.optLong("ts", 0L))
                }
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun toJson(usage: Map<String, Pair<Int, Long>>): String {
        val obj = JSONObject()
        for ((id, pair) in usage) {
            if (id.isBlank() || pair.first <= 0) continue
            obj.put(id, JSONObject().apply {
                put("n", pair.first)
                put("ts", pair.second)
            })
        }
        return obj.toString()
    }

    fun increment(usage: Map<String, Pair<Int, Long>>, id: String, now: Long): Map<String, Pair<Int, Long>> {
        if (id.isBlank()) return usage
        val next = usage.toMutableMap()
        val current = next[id]
        next[id] = (current?.first ?: 0) + 1 to now
        return next
    }

    fun rows(usage: Map<String, Pair<Int, Long>>): List<Row> {
        val knownIds = catalog.map { it.id }.toSet()
        val extra = usage.keys.filter { it !in knownIds }.sorted().map { id ->
            Section(id, id, "Другое", null)
        }
        return (catalog + extra).map { section ->
            val stored = usage[section.id]
            Row(section, stored?.first ?: 0, stored?.second ?: 0L)
        }.sortedWith(compareByDescending<Row> { it.opens }.thenBy { it.section.area }.thenBy { it.section.title })
    }

    /** Разделы приложения без Библии — медиа, детям, церковь и остальное. */
    fun nonBibleRows(usage: Map<String, Pair<Int, Long>>): List<Row> =
        rows(usage).filter { it.section.area != BIBLE_AREA }

    fun bibleRows(usage: Map<String, Pair<Int, Long>>): List<Row> =
        rows(usage).filter { it.section.area == BIBLE_AREA }

    fun rowsForArea(usage: Map<String, Pair<Int, Long>>, area: String): List<Row> =
        rows(usage).filter { it.section.area == area }

    fun areaOrder(): List<String> = listOf(
        "Медиа", "Жизнь", "Церковь", "ИИ", "Детям", "Эксперимент", "Настройки", "Другое",
    )

    /** Вкладки статистики: Библия и остальные области приложения. */
    fun statsTabAreas(): List<String> = listOf(BIBLE_AREA) + areaOrder().filter { it != "Другое" }

    fun band(value: Int, max: Int): Band {
        if (value <= 0 || max <= 0) return Band.NEVER
        val ratio = value.toFloat() / max.toFloat()
        return when {
            ratio >= 0.45f -> Band.OFTEN
            ratio >= 0.15f -> Band.SOMETIMES
            else -> Band.RARE
        }
    }
}
