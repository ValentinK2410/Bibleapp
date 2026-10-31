package com.example.bible.data.housecare

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.houseCareDataStore: DataStore<Preferences> by preferencesDataStore(name = "house_care")

private object HouseCareKeys {
    val DOCUMENT_JSON = stringPreferencesKey("house_care_json")
}

/** Хранит весь план уборки одним JSON-документом (см. [HouseCareData]). */
class HouseCareStore(context: Context) {

    private val app = context.applicationContext

    val data: Flow<HouseCareData> = app.houseCareDataStore.data.map { prefs ->
        normalize(HouseCareData.parse(prefs[HouseCareKeys.DOCUMENT_JSON]))
    }

    /** Старые данные: этажи без объекта относятся к первому объекту, при его отсутствии — к «Дому». */
    private fun normalize(cur: HouseCareData): HouseCareData {
        val needsHome = cur.places.isEmpty() && cur.floors.isNotEmpty()
        val places = if (needsHome) listOf(HousePlace(name = "Дом")) else cur.places
        val homeId = places.firstOrNull()?.id ?: ""
        val floors = cur.floors.map { floor ->
            val known = floor.placeId.isNotBlank() && places.any { it.id == floor.placeId }
            if (known) floor else floor.copy(placeId = homeId)
        }
        // Этажность объекта обязывает иметь столько этажей: недостающие добавляются.
        val withAuto = places.flatMap { place ->
            val owned = floors.filter { it.placeId == place.id }
            owned + autoFloors(place.id, place.floorsCount, startAt = owned.size + 1)
        }
        // Воскресенья в плане больше нет — комнаты этого дня переезжают на субботу.
        val zones = cur.zones.map { if (it.dayOfWeek == 7) it.copy(dayOfWeek = 6) else it }
        return cur.copy(places = places, floors = withAuto, zones = zones)
    }

    suspend fun snapshot(): HouseCareData = data.first()

    suspend fun save(data: HouseCareData) {
        app.houseCareDataStore.edit { prefs ->
            prefs[HouseCareKeys.DOCUMENT_JSON] = data.toJson().toString()
        }
    }

    /** Настройка плитки дня недели; старое фото удаляется, если его заменили или убрали. */
    suspend fun updateTileStyle(day: Int, transform: (WeekdayTileStyle) -> WeekdayTileStyle) = mutate { cur ->
        val existing = cur.tileStyleOf(day)
        val updated = transform(existing).copy(day = day)
        if (existing.photo != null && existing.photo != updated.photo) {
            HouseCarePhotoStorage.deleteFile(app, existing.photo)
        }
        val others = cur.tileStyles.filterNot { it.day == day }
        cur.copy(tileStyles = others + updated)
    }

    suspend fun addPlace(name: String, address: String, floorsCount: Int) = mutate { cur ->
        val place = HousePlace(name = name.trim(), address = address.trim(), floorsCount = floorsCount.coerceAtLeast(0))
        cur.copy(places = cur.places + place, floors = cur.floors + autoFloors(place.id, floorsCount))
    }

    /** Этажность можно только увеличивать: недостающие этажи добавляются, лишние не трогаются. */
    suspend fun updatePlace(id: String, name: String, address: String, floorsCount: Int) = mutate { cur ->
        val place = cur.placeById(id) ?: return@mutate cur
        val updated = place.copy(name = name.trim(), address = address.trim(), floorsCount = floorsCount.coerceAtLeast(0))
        val existing = cur.floorsOf(id)
        val extra = if (floorsCount > existing.size) autoFloors(id, floorsCount, startAt = existing.size + 1) else emptyList()
        cur.copy(
            places = cur.places.map { if (it.id == id) updated else it },
            floors = cur.floors + extra,
        )
    }

    /** Удаление объекта убирает его этажи, зоны, разделы и снимки. */
    suspend fun deletePlace(id: String) = mutate { cur ->
        val floorIds = cur.floorsOf(id).map { it.id }.toSet()
        val zoneIds = cur.zones.filter { it.floorId in floorIds }.map { it.id }.toSet()
        cur.tasks.filter { it.zoneId in zoneIds }.flatMap { it.photos() }.forEach { HouseCarePhotoStorage.deleteFile(app, it) }
        cur.copy(
            places = cur.places.filterNot { it.id == id },
            floors = cur.floors.filterNot { it.placeId == id },
            zones = cur.zones.filterNot { it.floorId in floorIds },
            tasks = cur.tasks.filterNot { it.zoneId in zoneIds },
            habits = cur.habits.filterNot { it.placeId == id },
        )
    }

    /** Детерминированные id: авто-этажи не плодят новые ключи при каждой загрузке документа. */
    private fun autoFloors(placeId: String, count: Int, startAt: Int = 1): List<HouseFloor> =
        (startAt..count).map { index ->
            HouseFloor(id = "auto-$placeId-$index", name = "$index этаж", placeId = placeId)
        }

    suspend fun addFloor(placeId: String, name: String) = mutate { cur ->
        cur.copy(floors = cur.floors + HouseFloor(name = name.trim(), placeId = placeId))
    }

    suspend fun renameFloor(id: String, name: String) = mutate { cur ->
        cur.copy(floors = cur.floors.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    /** Удаление этажа убирает его зоны, разделы и снимки. */
    suspend fun deleteFloor(id: String) = mutate { cur ->
        val zoneIds = cur.zones.filter { it.floorId == id }.map { it.id }
        val removedPhotos = cur.tasks.filter { it.zoneId in zoneIds }.flatMap { it.photos() }
        removedPhotos.forEach { HouseCarePhotoStorage.deleteFile(app, it) }
        cur.copy(
            floors = cur.floors.filterNot { it.id == id },
            zones = cur.zones.filterNot { it.floorId == id },
            tasks = cur.tasks.filterNot { it.zoneId in zoneIds },
        )
    }

    suspend fun addZone(floorId: String, dayOfWeek: Int, name: String) = mutate { cur ->
        cur.copy(zones = cur.zones + CleaningZone(floorId = floorId, dayOfWeek = dayOfWeek, name = name.trim()))
    }

    /** Комнату можно переименовать и перенести на другой день недели. */
    suspend fun updateZone(id: String, name: String, dayOfWeek: Int) = mutate { cur ->
        cur.copy(zones = cur.zones.map {
            if (it.id == id) it.copy(name = name.trim(), dayOfWeek = dayOfWeek.coerceIn(1, 7)) else it
        })
    }

    suspend fun deleteZone(id: String) = mutate { cur ->
        val removedPhotos = cur.tasks.filter { it.zoneId == id }.flatMap { it.photos() }
        removedPhotos.forEach { HouseCarePhotoStorage.deleteFile(app, it) }
        cur.copy(zones = cur.zones.filterNot { it.id == id }, tasks = cur.tasks.filterNot { it.zoneId == id })
    }

    suspend fun addTask(zoneId: String, name: String) = mutate { cur ->
        val nextOrder = (cur.tasksOf(zoneId).maxOfOrNull { it.order } ?: -1) + 1
        cur.copy(tasks = cur.tasks + CleaningTask(zoneId = zoneId, name = name.trim(), order = nextOrder))
    }

    /** Переставляет разделы внутри зоны: элемент с индекса [from] переезжает на позицию [to]. */
    suspend fun moveTask(zoneId: String, from: Int, to: Int) = mutate { cur ->
        val ordered = cur.tasksOf(zoneId).toMutableList()
        if (from !in ordered.indices) return@mutate cur
        val target = to.coerceIn(0, ordered.lastIndex)
        if (from == target) return@mutate cur
        ordered.add(target, ordered.removeAt(from))
        val idsInOrder = ordered.map { it.id }
        cur.copy(tasks = cur.tasks.map { task ->
            val index = idsInOrder.indexOf(task.id)
            if (index >= 0) task.copy(order = index) else task
        })
    }

    suspend fun renameTask(id: String, name: String) = mutate { cur ->
        cur.copy(tasks = cur.tasks.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    suspend fun deleteTask(id: String) = mutate { cur ->
        val photos = cur.taskById(id)?.photos().orEmpty()
        photos.forEach { HouseCarePhotoStorage.deleteFile(app, it) }
        cur.copy(tasks = cur.tasks.filterNot { it.id == id })
    }

    suspend fun updateTask(id: String, transform: (CleaningTask) -> CleaningTask) = mutate { cur ->
        cur.copy(tasks = cur.tasks.map { if (it.id == id) transform(it) else it })
    }

    /** Отчёт о текущей уборке: создаёт новый цикл, если активного нет. */
    suspend fun editActiveReport(taskId: String, transform: (CleaningReport) -> CleaningReport) =
        updateTask(taskId) { task ->
            val base = task.active ?: CleaningReport(id = UUID.randomUUID().toString())
            task.copy(active = transform(base))
        }

    /**
     * Снимок «до». Если предыдущая уборка уже завершена, она уходит в историю
     * и начинается новый цикл — так галочка на разделе сбрасывается.
     */
    suspend fun setBeforePhoto(taskId: String, fileName: String) = updateTask(taskId) { task ->
        val active = task.active
        val started = CleaningReport(beforePhoto = fileName, beforeAtMs = System.currentTimeMillis())
        if (active != null && active.isFinished) {
            task.copy(active = started, history = listOf(active) + task.history)
        } else {
            HouseCarePhotoStorage.deleteFile(app, active?.beforePhoto)
            task.copy(active = started.copy(id = active?.id ?: started.id, dateMs = active?.dateMs ?: started.dateMs))
        }
    }

    /** Снимок «после» завершает уборку; при перезаписи старый файл удаляется. */
    suspend fun setAfterPhoto(taskId: String, fileName: String) = editActiveReport(taskId) { report ->
        HouseCarePhotoStorage.deleteFile(app, report.afterPhoto)
        report.copy(afterPhoto = fileName, afterAtMs = System.currentTimeMillis())
    }

    suspend fun setReportDate(taskId: String, dateMs: Long) =
        editActiveReport(taskId) { it.copy(dateMs = dateMs) }

    /** Ручная правка времени уборки в минутах; null — считать по снимкам. */
    suspend fun setDurationMinutes(taskId: String, minutes: Int?) =
        editActiveReport(taskId) { report ->
            report.copy(durationMsOverride = minutes?.takeIf { it >= 0 }?.times(60_000L))
        }

    /** Удалить снимок «до» или «после» из текущего цикла. */
    suspend fun dropActiveReportPhoto(taskId: String, kind: String) = updateTask(taskId) { task ->
        val active = task.active ?: return@updateTask task
        val oldPath = if (kind == HouseCarePhotoStorage.KIND_BEFORE) active.beforePhoto else active.afterPhoto
        HouseCarePhotoStorage.deleteFile(app, oldPath)
        val cleared = when (kind) {
            HouseCarePhotoStorage.KIND_BEFORE -> active.copy(beforePhoto = null, beforeAtMs = null)
            else -> active.copy(afterPhoto = null, afterAtMs = null)
        }
        val isEmpty = cleared.beforePhoto == null && cleared.afterPhoto == null &&
            cleared.beforeAtMs == null && cleared.afterAtMs == null
        task.copy(active = if (isEmpty) null else cleared)
    }

    /** Привычка попадает в конец своего блока — утра или вечера. */
    suspend fun addHabit(placeId: String, kind: String, name: String) = mutate { cur ->
        val nextOrder = (cur.habitsOf(placeId, kind).maxOfOrNull { it.order } ?: -1) + 1
        cur.copy(
            habits = cur.habits + CleaningHabit(
                placeId = placeId,
                kind = kind,
                name = name.trim(),
                order = nextOrder,
            ),
        )
    }

    /** Смена блока — привычка переезжает в конец нового блока. */
    suspend fun updateHabit(id: String, name: String, kind: String) = mutate { cur ->
        val habit = cur.habits.firstOrNull { it.id == id } ?: return@mutate cur
        if (habit.kind == kind) {
            cur.copy(habits = cur.habits.map { if (it.id == id) it.copy(name = name.trim()) else it })
        } else {
            val nextOrder = (cur.habitsOf(habit.placeId, kind).maxOfOrNull { it.order } ?: -1) + 1
            cur.copy(habits = cur.habits.map {
                if (it.id == id) it.copy(name = name.trim(), kind = kind, order = nextOrder) else it
            })
        }
    }

    suspend fun deleteHabit(id: String) = mutate { cur ->
        cur.copy(habits = cur.habits.filterNot { it.id == id })
    }

    suspend fun moveHabit(placeId: String, kind: String, from: Int, to: Int) = mutate { cur ->
        val block = cur.habitsOf(placeId, kind).toMutableList()
        if (from !in block.indices) return@mutate cur
        val target = to.coerceIn(0, block.lastIndex)
        if (from == target) return@mutate cur
        block.add(target, block.removeAt(from))
        val idsInOrder = block.map { it.id }
        cur.copy(habits = cur.habits.map { habit ->
            val index = idsInOrder.indexOf(habit.id)
            if (index >= 0) habit.copy(order = index) else habit
        })
    }

    /** Галочка «выполнено» ставится на конкретную дату, поэтому на следующий день блок пустой. */
    suspend fun setHabitDone(id: String, date: String, done: Boolean) = mutate { cur ->
        cur.copy(habits = cur.habits.map { habit ->
            if (habit.id != id) habit
            else habit.copy(
                doneDates = if (done) (habit.doneDates + date).distinct().takeLast(400) else habit.doneDates - date,
            )
        })
    }

    private suspend fun mutate(block: (HouseCareData) -> HouseCareData) {
        save(block(snapshot()))
    }
}

/** Файлы «до/после» во внутренней памяти приложения; в JSON храним только имя файла. */
object HouseCarePhotoStorage {
    const val SUBDIR = "house_care_photos"
    const val KIND_BEFORE = "before"
    const val KIND_AFTER = "after"

    fun dir(context: Context): File = File(context.filesDir, SUBDIR).apply { mkdirs() }

    fun createImageFile(context: Context, kind: String): File {
        val name = "${kind}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        return File(dir(context), name)
    }

    /** Скопировать выбранный из галереи uri во внутренний файл; true — успех. */
    fun copyUriToJpegFile(context: Context, uri: Uri, dest: File): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { out -> input.copyTo(out) }
        } != null
    } catch (_: Exception) {
        if (dest.exists()) dest.delete()
        false
    }

    fun resolve(context: Context, name: String?): File? {
        if (name.isNullOrBlank()) return null
        val file = File(dir(context), name)
        return if (file.exists()) file else null
    }

    fun deleteFile(context: Context, name: String?) {
        val file = resolve(context, name) ?: return
        runCatching { file.delete() }
    }
}
