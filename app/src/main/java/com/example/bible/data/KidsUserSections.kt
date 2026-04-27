package com.example.bible.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Пользовательские правки раздела «Детям»: порядок и видимость карточек хаба, свои разделы,
 * замена картинок/звуков и режим просмотра для плиток с фото.
 */
data class KidsUserSectionsState(
    /** Порядок маршрутов; null = как в приложении по умолчанию. */
    val order: List<String>?,
    val hiddenRoutes: Set<String>,
    val titleOverrides: Map<String, String>,
    val subtitleOverrides: Map<String, String>,
    val customHub: List<KidsCustomHubEntry>,
    /** Ключ — маршрут (в т.ч. kids_custom_*). */
    val picturedEdits: Map<String, KidsPicturedSectionEdits>,
) {
    companion object {
        fun empty(): KidsUserSectionsState = KidsUserSectionsState(
            order = null,
            hiddenRoutes = emptySet(),
            titleOverrides = emptyMap(),
            subtitleOverrides = emptyMap(),
            customHub = emptyList(),
            picturedEdits = emptyMap(),
        )

        fun fromJson(raw: String?): KidsUserSectionsState {
            if (raw.isNullOrBlank()) return empty()
            return try {
                parse(JSONObject(raw))
            } catch (_: Exception) {
                empty()
            }
        }

        private fun parse(jo: JSONObject): KidsUserSectionsState {
            val order = if (jo.has("order")) {
                val a = jo.getJSONArray("order")
                (0 until a.length()).map { a.getString(it) }
            } else {
                null
            }
            val hidden = if (jo.has("hidden")) {
                val a = jo.getJSONArray("hidden")
                (0 until a.length()).map { it -> a.getString(it) }.toSet()
            } else {
                emptySet()
            }
            val titles = mutableMapOf<String, String>()
            if (jo.has("titles")) {
                val o = jo.getJSONObject("titles")
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    titles[k] = o.getString(k)
                }
            }
            val subtitles = mutableMapOf<String, String>()
            if (jo.has("subtitles")) {
                val o = jo.getJSONObject("subtitles")
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    subtitles[k] = o.getString(k)
                }
            }
            val custom = mutableListOf<KidsCustomHubEntry>()
            if (jo.has("customHub")) {
                val a = jo.getJSONArray("customHub")
                for (i in 0 until a.length()) {
                    custom.add(KidsCustomHubEntry.fromJson(a.getJSONObject(i)))
                }
            }
            val pictured = mutableMapOf<String, KidsPicturedSectionEdits>()
            if (jo.has("pictured")) {
                val o = jo.getJSONObject("pictured")
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    pictured[k] = KidsPicturedSectionEdits.fromJson(o.getJSONObject(k))
                }
            }
            return KidsUserSectionsState(order, hidden, titles, subtitles, custom, pictured)
        }

        fun toJson(s: KidsUserSectionsState): String {
            val jo = JSONObject()
            s.order?.let {
                jo.put("order", JSONArray().apply { it.forEach { put(it) } })
            }
            if (s.hiddenRoutes.isNotEmpty()) {
                jo.put("hidden", JSONArray().apply { s.hiddenRoutes.forEach { put(it) } })
            }
            if (s.titleOverrides.isNotEmpty()) {
                jo.put("titles", JSONObject().apply { s.titleOverrides.forEach { (k, v) -> put(k, v) } })
            }
            if (s.subtitleOverrides.isNotEmpty()) {
                jo.put("subtitles", JSONObject().apply { s.subtitleOverrides.forEach { (k, v) -> put(k, v) } })
            }
            if (s.customHub.isNotEmpty()) {
                jo.put("customHub", JSONArray().apply { s.customHub.forEach { put(it.toJson()) } })
            }
            if (s.picturedEdits.isNotEmpty()) {
                val po = JSONObject()
                s.picturedEdits.forEach { (k, v) -> po.put(k, v.toJson()) }
                jo.put("pictured", po)
            }
            return jo.toString()
        }
    }

    fun toJsonString(): String = Companion.toJson(this)
}

data class KidsCustomHubEntry(
    val route: String,
    val title: String,
    val subtitle: String,
    /** Pictures, Musician, Pesnopenie, Videos, Audios */
    val cardStyle: String,
    val emojiThumb: String?,
    val tileStyle: String,
    val items: List<KidsPicturedItemJson>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("route", route)
        put("title", title)
        put("subtitle", subtitle)
        put("cardStyle", cardStyle)
        emojiThumb?.let { put("emojiThumb", it) }
        put("tileStyle", tileStyle)
        put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(j: JSONObject): KidsCustomHubEntry = KidsCustomHubEntry(
            route = j.getString("route"),
            title = j.getString("title"),
            subtitle = j.optString("subtitle", ""),
            cardStyle = j.optString("cardStyle", "Pictures"),
            emojiThumb = j.optString("emojiThumb", "").takeIf { it.isNotBlank() },
            tileStyle = j.optString("tileStyle", "square"),
            items = if (j.has("items")) {
                val a = j.getJSONArray("items")
                (0 until a.length()).map { KidsPicturedItemJson.fromJson(a.getJSONObject(it)) }
            } else {
                emptyList()
            },
        )
    }
}

data class KidsPicturedItemJson(
    val itemKey: String,
    val label: String,
    val speak: String,
    val emoji: String,
    val drawableName: String?,
    val rawSoundName: String?,
    val soundPitch: Float,
    val customImagePath: String?,
    val customSoundPath: String?,
    val detailFullScreen: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("itemKey", itemKey)
        put("label", label)
        put("speak", speak)
        put("emoji", emoji)
        drawableName?.let { put("drawable", it) }
        rawSoundName?.let { put("raw", it) }
        put("soundPitch", soundPitch.toDouble())
        customImagePath?.let { put("img", it) }
        customSoundPath?.let { put("snd", it) }
        put("full", detailFullScreen)
    }

    companion object {
        fun fromJson(j: JSONObject): KidsPicturedItemJson = KidsPicturedItemJson(
            itemKey = j.optString("itemKey", j.getString("label")),
            label = j.getString("label"),
            speak = j.optString("speak", j.getString("label")),
            emoji = j.optString("emoji", "🌿"),
            drawableName = j.optString("drawable", "").takeIf { it.isNotBlank() },
            rawSoundName = j.optString("raw", "").takeIf { it.isNotBlank() },
            soundPitch = j.optDouble("soundPitch", 1.0).toFloat(),
            customImagePath = j.optString("img", "").takeIf { it.isNotBlank() },
            customSoundPath = j.optString("snd", "").takeIf { it.isNotBlank() },
            detailFullScreen = j.optBoolean("full", true),
        )
    }
}

data class KidsPicturedSectionEdits(
    val byKey: Map<String, KidsPicturedItemPatch>,
    val added: List<KidsPicturedItemJson>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        if (byKey.isNotEmpty()) {
            val o = JSONObject()
            byKey.forEach { (k, v) -> o.put(k, v.toJson()) }
            put("byKey", o)
        }
        if (added.isNotEmpty()) {
            put("added", JSONArray().apply { added.forEach { put(it.toJson()) } })
        }
    }

    companion object {
        fun fromJson(j: JSONObject): KidsPicturedSectionEdits {
            val byKey = mutableMapOf<String, KidsPicturedItemPatch>()
            if (j.has("byKey")) {
                val o = j.getJSONObject("byKey")
                val it = o.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    byKey[k] = KidsPicturedItemPatch.fromJson(o.getJSONObject(k))
                }
            }
            val added = mutableListOf<KidsPicturedItemJson>()
            if (j.has("added")) {
                val a = j.getJSONArray("added")
                for (i in 0 until a.length()) {
                    added.add(KidsPicturedItemJson.fromJson(a.getJSONObject(i)))
                }
            }
            return KidsPicturedSectionEdits(byKey, added)
        }
    }
}

data class KidsPicturedItemPatch(
    val label: String?,
    val speak: String?,
    val emoji: String?,
    val customImagePath: String?,
    val customSoundPath: String?,
    val clearCustomImage: Boolean,
    val clearCustomSound: Boolean,
    val detailFullScreen: Boolean?,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        label?.let { put("label", it) }
        speak?.let { put("speak", it) }
        emoji?.let { put("emoji", it) }
        customImagePath?.let { put("img", it) }
        customSoundPath?.let { put("snd", it) }
        if (clearCustomImage) put("clearImg", true)
        if (clearCustomSound) put("clearSnd", true)
        detailFullScreen?.let { put("full", it) }
    }

    companion object {
        fun fromJson(j: JSONObject): KidsPicturedItemPatch = KidsPicturedItemPatch(
            label = j.optString("label", "").takeIf { it.isNotBlank() },
            speak = j.optString("speak", "").takeIf { it.isNotBlank() },
            emoji = j.optString("emoji", "").takeIf { it.isNotBlank() },
            customImagePath = j.optString("img", "").takeIf { it.isNotBlank() },
            customSoundPath = j.optString("snd", "").takeIf { it.isNotBlank() },
            clearCustomImage = j.optBoolean("clearImg", false),
            clearCustomSound = j.optBoolean("clearSnd", false),
            detailFullScreen = if (j.has("full")) j.getBoolean("full") else null,
        )
    }
}

object KidsPicturedRoutes {
    val all: Set<String> = setOf(
        "kids_animals",
        "kids_fish",
        "kids_snakes",
        "kids_insects",
        "kids_trees",
        "kids_plants",
    )

    fun isPicturedRoute(route: String): Boolean =
        route in all || route.startsWith("kids_custom_")

    fun defaultItems(route: String): List<KidsPicturedItem>? = when (route) {
        "kids_animals" -> KidsPicturedTopics.animals
        "kids_fish" -> KidsPicturedTopics.fish
        "kids_snakes" -> KidsPicturedTopics.snakes
        "kids_insects" -> KidsPicturedTopics.insects
        "kids_trees" -> KidsPicturedTopics.trees
        "kids_plants" -> KidsPicturedTopics.plants
        else -> null
    }
}

object KidsUserSectionsMerge {

    fun mergePicturedItems(
        route: String,
        context: android.content.Context,
        state: KidsUserSectionsState?,
    ): List<KidsPicturedItem> {
        val st = state ?: return KidsPicturedRoutes.defaultItems(route) ?: emptyList()
        if (route.startsWith("kids_custom_")) {
            val entry = st.customHub.find { it.route == route } ?: return emptyList()
            val base = entry.items.map { it.toRuntimeItem(context) }
            val edits = st.picturedEdits[route] ?: return base
            val merged = base.map { item ->
                val p = edits.byKey[item.itemKey] ?: edits.byKey[item.label]
                applyPatch(item, p)
            }
            return merged + edits.added.map { it.toRuntimeItem(context) }
        }
        val base = KidsPicturedRoutes.defaultItems(route) ?: return emptyList()
        val edits = st.picturedEdits[route] ?: return base
        val merged = base.map { item ->
            val p = edits.byKey[item.itemKey] ?: edits.byKey[item.label]
            applyPatch(item, p)
        }
        val extras = edits.added.map { it.toRuntimeItem(context) }
        return merged + extras
    }

    private fun applyPatch(item: KidsPicturedItem, p: KidsPicturedItemPatch?): KidsPicturedItem {
        if (p == null) return item
        return item.copy(
            label = p.label ?: item.label,
            speak = p.speak ?: item.speak,
            emoji = p.emoji ?: item.emoji,
            customImagePath = when {
                p.clearCustomImage -> null
                p.customImagePath != null -> p.customImagePath
                else -> item.customImagePath
            },
            customSoundPath = when {
                p.clearCustomSound -> null
                p.customSoundPath != null -> p.customSoundPath
                else -> item.customSoundPath
            },
            detailFullScreen = p.detailFullScreen ?: item.detailFullScreen,
        )
    }

    private fun KidsPicturedItemJson.toRuntimeItem(context: android.content.Context): KidsPicturedItem {
        val img = drawableName?.let { name ->
            val id = context.resources.getIdentifier(name, "drawable", context.packageName)
            if (id != 0) id else null
        }
        val snd = rawSoundName?.let { name ->
            val id = context.resources.getIdentifier(name, "raw", context.packageName)
            if (id != 0) id else null
        }
        return KidsPicturedItem(
            label = label,
            speak = speak,
            emoji = emoji,
            imageRes = img,
            soundRes = snd,
            soundPitch = soundPitch,
            itemKey = itemKey,
            customImagePath = customImagePath,
            customSoundPath = customSoundPath,
            detailFullScreen = detailFullScreen,
        )
    }
}
