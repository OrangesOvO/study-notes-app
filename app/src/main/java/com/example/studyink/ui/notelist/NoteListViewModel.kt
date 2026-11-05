package com.example.studyink.ui.notelist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.studyink.data.NoteEntity
import com.example.studyink.data.NoteRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteListViewModel(private val repo: NoteRepository) : ViewModel() {

    val notes: StateFlow<List<NoteEntity>> = repo.observeNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteNote(id: Long) {
        viewModelScope.launch { repo.deleteNote(id) }
    }

    companion object {
        fun factory(repo: NoteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NoteListViewModel(repo) as T
            }
    }
}
