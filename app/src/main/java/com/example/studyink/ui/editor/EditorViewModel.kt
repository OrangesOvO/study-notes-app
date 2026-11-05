package com.example.studyink.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.studyink.data.NoteEntity
import com.example.studyink.data.NoteRepository
import com.example.studyink.model.PenType
import com.example.studyink.model.Stroke
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditorViewModel(
    private val repo: NoteRepository,
    private val noteId: Long
) : ViewModel() {

    val note: StateFlow<NoteEntity?> = repo.observeNote(noteId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentPage = MutableStateFlow(0)

    val strokes: StateFlow<List<Stroke>> = currentPage
        .flatMapLatest { page -> repo.observeStrokes(noteId, page) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var penType by mutableStateOf(PenType.PEN)
        private set
    var penColor by mutableStateOf(Color(0xFF1A1A1A))
        private set

    // 撤销 / 重做栈（会话内）
    var undoStack by mutableStateOf(listOf<Stroke>())
        private set
    var redoStack by mutableStateOf(listOf<Stroke>())
        private set

    fun setPenType(type: PenType) {
        penType = type
    }

    fun setPenColor(color: Color) {
        penColor = color
    }

    fun penWidth(): Float = when (penType) {
        PenType.PEN -> 8f
        PenType.MARKER -> 20f
        PenType.HIGHLIGHTER -> 36f
    }

    fun setPage(page: Int) {
        currentPage.value = page
        undoStack = emptyList()
        redoStack = emptyList()
    }

    fun addStroke(stroke: Stroke) {
        val page = currentPage.value
        viewModelScope.launch {
            val newId = repo.addStroke(noteId, page, stroke)
            undoStack = undoStack + stroke.copy(id = newId, noteId = noteId, pageIndex = page)
            redoStack = emptyList()
        }
    }

    fun undo() {
        val last = undoStack.lastOrNull() ?: return
        viewModelScope.launch {
            repo.deleteStroke(last.id)
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + last
        }
    }

    fun redo() {
        val last = redoStack.lastOrNull() ?: return
        viewModelScope.launch {
            val newId = repo.addStroke(noteId, last.pageIndex, last.copy(id = 0L))
            undoStack = undoStack + last.copy(id = newId)
            redoStack = redoStack.dropLast(1)
        }
    }

    companion object {
        fun factory(repo: NoteRepository, noteId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EditorViewModel(repo, noteId) as T
            }
    }
}
