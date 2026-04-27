package com.example.bible.ui.church

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bible.data.church.ChurchParticipant
import com.example.bible.data.church.ChurchRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChurchViewModel(
    app: Application,
) : AndroidViewModel(app) {

    private val repo = ChurchRepository(app)

    val participants: StateFlow<List<ChurchParticipant>> = repo.participants.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun upsertParticipant(p: ChurchParticipant) {
        viewModelScope.launch {
            repo.upsertParticipant(p)
        }
    }

    fun deleteParticipant(id: String) {
        viewModelScope.launch {
            repo.deleteParticipant(id)
        }
    }

    companion object {
        fun factory(app: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChurchViewModel(app) as T
        }
    }
}
