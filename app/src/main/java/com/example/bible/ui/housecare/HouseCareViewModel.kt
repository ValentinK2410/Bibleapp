package com.example.bible.ui.housecare

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bible.data.housecare.HouseCareData
import com.example.bible.data.housecare.HouseCarePhotoStorage
import com.example.bible.data.housecare.HouseCareStore
import com.example.bible.data.housecare.WeekdayTileStyle
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HouseCareViewModel(
    app: Application,
) : AndroidViewModel(app) {

    private val store = HouseCareStore(app)

    val data: StateFlow<HouseCareData> = store.data.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HouseCareData.Empty,
    )

    private fun edit(block: suspend HouseCareStore.() -> Unit) {
        viewModelScope.launch { store.block() }
    }

    fun updateTileStyle(day: Int, transform: (WeekdayTileStyle) -> WeekdayTileStyle) =
        edit { updateTileStyle(day, transform) }

    fun addPlace(name: String, address: String, floorsCount: Int) = edit { addPlace(name, address, floorsCount) }

    fun updatePlace(id: String, name: String, address: String, floorsCount: Int) =
        edit { updatePlace(id, name, address, floorsCount) }

    fun deletePlace(id: String) = edit { deletePlace(id) }

    fun addFloor(placeId: String, name: String) = edit { addFloor(placeId, name) }

    fun renameFloor(id: String, name: String) = edit { renameFloor(id, name) }

    fun deleteFloor(id: String) = edit { deleteFloor(id) }

    fun addZone(floorId: String, dayOfWeek: Int, name: String) = edit { addZone(floorId, dayOfWeek, name) }

    fun updateZone(id: String, name: String, dayOfWeek: Int) = edit { updateZone(id, name, dayOfWeek) }

    fun deleteZone(id: String) = edit { deleteZone(id) }

    fun addHabit(placeId: String, kind: String, name: String) = edit { addHabit(placeId, kind, name) }

    fun updateHabit(id: String, name: String, kind: String) = edit { updateHabit(id, name, kind) }

    fun deleteHabit(id: String) = edit { deleteHabit(id) }

    fun moveHabit(placeId: String, kind: String, from: Int, to: Int) = edit { moveHabit(placeId, kind, from, to) }

    fun setHabitDone(id: String, date: String, done: Boolean) = edit { setHabitDone(id, date, done) }

    fun addTask(zoneId: String, name: String) = edit { addTask(zoneId, name) }

    fun renameTask(id: String, name: String) = edit { renameTask(id, name) }

    fun deleteTask(id: String) = edit { deleteTask(id) }

    fun moveTask(zoneId: String, from: Int, to: Int) = edit { moveTask(zoneId, from, to) }

    fun setBeforePhoto(taskId: String, fileName: String) = edit { setBeforePhoto(taskId, fileName) }

    fun setAfterPhoto(taskId: String, fileName: String) = edit { setAfterPhoto(taskId, fileName) }

    fun removePhoto(taskId: String, kind: String) = edit { dropActiveReportPhoto(taskId, kind) }

    fun setReportDate(taskId: String, dateMs: Long) = edit { setReportDate(taskId, dateMs) }

    fun setDurationMinutes(taskId: String, minutes: Int?) = edit { setDurationMinutes(taskId, minutes) }

    /** Пустой файл для снимка; имя позже становится ссылкой в отчёте. */
    fun createPhotoFile(kind: String): File = HouseCarePhotoStorage.createImageFile(getApplication(), kind)

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HouseCareViewModel(app) as T
        }
    }
}
