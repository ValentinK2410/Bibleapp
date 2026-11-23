package com.example.bible.ui.mynotes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bible.data.mynotes.MyNote
import com.example.bible.data.mynotes.MyNoteKind
import com.example.bible.data.mynotes.MyNoteSort
import com.example.bible.data.mynotes.MyNotesDocument
import com.example.bible.data.mynotes.MyNotesStore
import java.util.UUID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Заметка, удалённая в редакторе: список покажет «Вернуть», когда снова откроется. */
internal object MyNotesUndo {
    var note: MyNote? = null

    fun offer(note: MyNote) {
        this.note = note
    }

    fun consume(): MyNote? = note.also { note = null }
}

data class MyNotesUi(
    val document: MyNotesDocument = MyNotesDocument.Empty,
    val loaded: Boolean = false,
)

class MyNotesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = MyNotesStore(app)

    val ui: StateFlow<MyNotesUi> = store.document
        .map { MyNotesUi(it, loaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyNotesUi())

    fun create(kind: MyNoteKind, onReady: (String) -> Unit) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            store.update { doc ->
                val order = (doc.notes.minOfOrNull { it.order } ?: 0) - 1
                doc.copy(
                    notes = doc.notes + MyNote(
                        id = id,
                        kind = kind,
                        createdAt = now,
                        updatedAt = now,
                        order = order,
                    ),
                )
            }
            onReady(id)
        }
    }

    suspend fun load(id: String): MyNote? =
        store.document.first().notes.find { it.id == id }

    suspend fun save(note: MyNote) {
        val stamped = note.copy(updatedAt = System.currentTimeMillis())
        store.update { it.withContent(stamped) }
    }

    suspend fun remove(id: String) {
        store.update { it.delete(id) }
    }

    suspend fun restore(note: MyNote) {
        store.update { it.restore(note) }
    }

    suspend fun reorderNotes(orderedIds: List<String>) {
        store.update { it.reorderTo(orderedIds) }
    }

    fun setPinned(id: String, pinned: Boolean) {
        viewModelScope.launch {
            store.update { it.setPinned(id, pinned, System.currentTimeMillis()) }
        }
    }

    fun toggleDone(id: String) {
        viewModelScope.launch {
            store.update { it.toggleDone(id, System.currentTimeMillis()) }
        }
    }

    fun toggleCheck(noteId: String, itemId: String) {
        viewModelScope.launch {
            store.update { it.toggleCheck(noteId, itemId, System.currentTimeMillis()) }
        }
    }

    fun setSort(sort: MyNoteSort) {
        viewModelScope.launch { store.update { it.copy(sort = sort) } }
    }

    fun setFilter(kind: MyNoteKind?) {
        viewModelScope.launch { store.update { it.copy(filterKind = kind) } }
    }

    fun setGrid(grid: Boolean) {
        viewModelScope.launch { store.update { it.copy(grid = grid) } }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MyNotesViewModel(app) as T
            }
    }
}
