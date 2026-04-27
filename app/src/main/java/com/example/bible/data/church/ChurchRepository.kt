package com.example.bible.data.church

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.churchDataStore by preferencesDataStore(name = "my_church")

private object ChurchKeys {
    val PARTICIPANTS_JSON = stringPreferencesKey("church_participants_json")
}

class ChurchRepository(
    context: Context,
) {
    private val app = context.applicationContext

    val participants: Flow<List<ChurchParticipant>> = app.churchDataStore.data.map { prefs ->
        ChurchParticipant.parseList(prefs[ChurchKeys.PARTICIPANTS_JSON] ?: "[]")
    }

    suspend fun snapshotParticipants(): List<ChurchParticipant> = participants.first()

    suspend fun saveParticipants(list: List<ChurchParticipant>) {
        app.churchDataStore.edit { prefs ->
            prefs[ChurchKeys.PARTICIPANTS_JSON] = ChurchParticipant.toJsonArray(list)
        }
    }

    suspend fun upsertParticipant(p: ChurchParticipant) {
        val cur = snapshotParticipants().filter { it.id != p.id }
        saveParticipants(cur + p)
    }

    suspend fun deleteParticipant(id: String) {
        saveParticipants(snapshotParticipants().filter { it.id != id })
    }
}
