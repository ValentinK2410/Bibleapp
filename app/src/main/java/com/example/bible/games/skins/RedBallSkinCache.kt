package com.example.bible.games.skins

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Уже загруженные PNG главы. Декодируем один раз на процесс и держим в памяти:
 * в кадре только drawImage, без повторного чтения assets.
 * Классика возвращает null — её рисуют процедуры.
 */
class RedBallSkinBitmaps(
    val spec: RedBallSkin,
    private val images: Map<RedBallSkinSlot, ImageBitmap>,
) {
    val id: RedBallSkinId get() = spec.id

    fun bitmap(slot: RedBallSkinSlot): ImageBitmap = images.getValue(slot)
}

object RedBallSkinCache {
    private const val TAG = "RedBallSkin"
    private val loaded = HashMap<RedBallSkinId, RedBallSkinBitmaps>()

    fun load(context: Context, id: RedBallSkinId): RedBallSkinBitmaps? {
        if (id.procedural) return null
        synchronized(loaded) {
            loaded[id]?.let { return it }
            val spec = RedBallSkins.chapter(id) ?: return null
            val bitmaps = decode(context.applicationContext, spec) ?: return null
            loaded[id] = bitmaps
            return bitmaps
        }
    }

    private fun decode(context: Context, spec: RedBallSkin): RedBallSkinBitmaps? {
        val images = HashMap<RedBallSkinSlot, ImageBitmap>(RedBallSkinSlot.entries.size)
        for (slot in RedBallSkinSlot.entries) {
            val path = spec.assetPath(slot)
            val bitmap = try {
                context.assets.open(path).use { stream ->
                    val opts = BitmapFactory.Options().apply {
                        inScaled = false
                        inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                    }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Не открылся $path", error)
                return null
            }
            if (bitmap == null) {
                Log.e(TAG, "Пустой декод $path")
                return null
            }
            images[slot] = bitmap.asImageBitmap()
        }
        return RedBallSkinBitmaps(spec, images)
    }
}

/** Выбор оформления. Лежит в своих prefs и не попадает в JSON уровня. */
class RedBallSkinSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun current(): RedBallSkinId = RedBallSkinId.fromStored(prefs.getString(KEY, null))

    fun save(id: RedBallSkinId) {
        prefs.edit().putString(KEY, id.storageKey).apply()
    }

    private companion object {
        const val PREFS = "redball_skin"
        const val KEY = "skin"
    }
}
