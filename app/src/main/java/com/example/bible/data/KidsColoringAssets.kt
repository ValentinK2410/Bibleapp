package com.example.bible.data

import android.content.Context
import org.json.JSONObject

/** Область раскраски из JSON `kids-coloring/v1`. Координаты пути — в квадрате 800×800. */
data class KidsColorOutline(
    val id: String,
    val d: String,
    val hintArgb: Int,
)

fun parseColoringOutlines(json: String, small: Boolean): List<KidsColorOutline> {
    val root = JSONObject(json)
    val levelName = if (small) "small" else "detailed"
    val regions = root.getJSONObject("levels").getJSONObject(levelName).getJSONArray("regions")
    return buildList {
        for (index in 0 until regions.length()) {
            val item = regions.getJSONObject(index)
            add(
                KidsColorOutline(
                    id = item.getString("id"),
                    d = item.getString("d"),
                    hintArgb = parseColorHint(item.optString("hint", "#FFFFFF")),
                ),
            )
        }
    }
}

fun loadColoringOutlines(context: Context, assetName: String, small: Boolean): List<KidsColorOutline> {
    val path = "kids/coloring/$assetName"
    return runCatching {
        context.assets.open(path).bufferedReader().use { reader ->
            parseColoringOutlines(reader.readText(), small)
        }
    }.getOrDefault(emptyList())
}

private fun parseColorHint(raw: String): Int {
    val hex = raw.trim().removePrefix("#")
    val value = hex.toLongOrNull(16) ?: return 0xFFFFFFFF.toInt()
    return when (hex.length) {
        6 -> (0xFF000000 or value).toInt()
        8 -> value.toInt()
        else -> 0xFFFFFFFF.toInt()
    }
}
