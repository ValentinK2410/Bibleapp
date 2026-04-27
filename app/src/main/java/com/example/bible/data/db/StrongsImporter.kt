package com.example.bible.data.db

import android.content.Context
import org.json.JSONObject

object StrongsImporter {

    private const val BATCH = 400

    fun importFromAssetsIfEmpty(context: Context, db: StudyDatabase) {
        val dao = db.studyDao()
        if (dao.countStrongsEntries() > 0) return
        val text = try {
            context.assets.open("strongs_dictionary.json").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return
        }
        val json = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        val keys = json.keys()
        val batch = ArrayList<StrongsEntryEntity>(BATCH)
        db.runInTransaction {
            while (keys.hasNext()) {
                val k = keys.next()
                val obj = json.optJSONObject(k) ?: continue
                batch.add(
                    StrongsEntryEntity(
                        code = k,
                        lemma = obj.optString("l", ""),
                        translit = obj.optString("t", ""),
                        pronunciation = obj.optString("p", ""),
                        definition = obj.optString("d", ""),
                        kjvUsage = obj.optString("k", ""),
                        origin = obj.optString("o", ""),
                    ),
                )
                if (batch.size >= BATCH) {
                    dao.insertStrongs(batch)
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) {
                dao.insertStrongs(batch)
            }
        }
    }
}
