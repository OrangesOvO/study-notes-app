package com.example.studyink.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.studyink.data.NoteEntity
import com.example.studyink.data.NoteRepository
import com.example.studyink.model.PageBackground
import com.example.studyink.model.Stroke
import com.example.studyink.model.Tool
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 撤销记录类型 */
enum class OpKind { ADDED, ERASED }

/** 一条可撤销操作 */
data class UndoRecord(val stroke: Stroke, val kind: OpKind)

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

    // ---- 书写工具状态 ----
    var tool by mutableStateOf(Tool.PEN)
        private set
    var penColor by mutableStateOf(Color(0xFF1A1A1A))
    var penWidth by mutableStateOf(Tool.PEN.defaultWidth)
        private set
    var recentColors by mutableStateOf(listOf<Int>())

    // ---- 撤销 / 重做（会话内） ----
    var undoStack by mutableStateOf(listOf<UndoRecord>())
        private set
    var redoStack by mutableStateOf(listOf<UndoRecord>())
        private set

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun selectTool(t: Tool) {
        tool = t
        penWidth = t.defaultWidth
    }

    fun updatePenWidth(w: Float) {
        penWidth = w.coerceIn(2f, 48f)
    }

    fun updatePenColor(c: Color) {
        penColor = c
        val argb = c.toArgb()
        recentColors = (listOf(argb) + recentColors.filter { it != argb }).take(6)
    }

    // ---- 页 ----

    fun setPage(page: Int) {
        currentPage.value = page
        undoStack = emptyList()
        redoStack = emptyList()
    }

    /** 末尾加一页并跳过去 */
    fun addPage() {
        viewModelScope.launch {
            val newCount = repo.appendPage(noteId)
            currentPage.value = (newCount - 1).coerceAtLeast(0)
            undoStack = emptyList()
            redoStack = emptyList()
        }
    }

    /** 删除当前页（至少保留一页），后续页整体前移 */
    fun deleteCurrentPage() {
        val page = currentPage.value
        viewModelScope.launch {
            val n = repo.getNote(noteId) ?: return@launch
            if (n.pageCount <= 1) return@launch
            repo.deletePage(noteId, page)
            currentPage.value = page.coerceAtMost(n.pageCount - 2).coerceAtLeast(0)
            undoStack = emptyList()
            redoStack = emptyList()
        }
    }

    fun rename(title: String) {
        val t = title.trim()
        if (t.isEmpty()) return
        viewModelScope.launch { repo.renameNote(noteId, t) }
    }

    fun applyBackground(bg: PageBackground) {
        viewModelScope.launch { repo.setBackground(noteId, bg) }
    }

    // ---- 笔迹 ----

    fun addStroke(stroke: Stroke) {
        val page = currentPage.value
        viewModelScope.launch {
            val newId = repo.addStroke(noteId, page, stroke)
            undoStack = undoStack + UndoRecord(
                stroke.copy(id = newId, noteId = noteId, pageIndex = page),
                OpKind.ADDED
            )
            redoStack = emptyList()
        }
    }

    fun eraseStroke(stroke: Stroke) {
        if (stroke.id == 0L) return
        viewModelScope.launch {
            repo.deleteStroke(stroke.id)
            undoStack = undoStack + UndoRecord(stroke, OpKind.ERASED)
            redoStack = emptyList()
        }
    }

    fun undo() {
        val last = undoStack.lastOrNull() ?: return
        viewModelScope.launch {
            var redoRecord = last
            when (last.kind) {
                OpKind.ADDED -> repo.deleteStroke(last.stroke.id)
                OpKind.ERASED -> {
                    val newId = repo.addStroke(
                        noteId, last.stroke.pageIndex, last.stroke.copy(id = 0L)
                    )
                    redoRecord = last.copy(stroke = last.stroke.copy(id = newId))
                }
            }
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + redoRecord
        }
    }

    fun redo() {
        val last = redoStack.lastOrNull() ?: return
        viewModelScope.launch {
            var undoRecord = last
            when (last.kind) {
                OpKind.ADDED -> {
                    val newId = repo.addStroke(
                        noteId, last.stroke.pageIndex, last.stroke.copy(id = 0L)
                    )
                    undoRecord = last.copy(stroke = last.stroke.copy(id = newId))
                }
                OpKind.ERASED -> repo.deleteStroke(last.stroke.id)
            }
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + undoRecord
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
