package com.example.studyink.data

import com.example.studyink.model.NoteType
import com.example.studyink.model.PenType
import com.example.studyink.model.Stroke
import com.example.studyink.util.StrokeSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository(private val dao: NoteDao) {

    fun observeNotes(): Flow<List<NoteEntity>> = dao.observeNotes()

    fun observeNote(id: Long): Flow<NoteEntity?> = dao.observeNote(id)

    suspend fun getNote(id: Long): NoteEntity? = dao.getNote(id)

    suspend fun createBlankNote(title: String): Long =
        dao.insertNote(
            NoteEntity(title = title, type = NoteType.BLANK.name, pageCount = 1)
        )

    suspend fun createPdfNote(title: String, pdfPath: String, pageCount: Int): Long =
        dao.insertNote(
            NoteEntity(title = title, type = NoteType.PDF.name, pdfPath = pdfPath, pageCount = pageCount)
        )

    suspend fun updateCurrentPage(id: Long, page: Int) {
        val n = dao.getNote(id) ?: return
        dao.updateNote(n.copy(currentPage = page, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteNote(id: Long) {
        val n = dao.getNote(id) ?: return
        dao.deleteNote(n)
        dao.deleteStrokesForNote(id)
    }

    fun observeStrokes(noteId: Long, pageIndex: Int): Flow<List<Stroke>> =
        dao.observeStrokes(noteId, pageIndex).map { list -> list.map { it.toStroke() } }

    suspend fun getAllStrokes(noteId: Long): List<Stroke> =
        dao.getAllStrokesForNote(noteId).map { it.toStroke() }

    /** 新增一条笔迹，返回落库后的 id */
    suspend fun addStroke(noteId: Long, pageIndex: Int, stroke: Stroke): Long {
        touch(noteId)
        return dao.insertStroke(stroke.toEntity(noteId, pageIndex))
    }

    suspend fun deleteStroke(strokeId: Long) {
        dao.deleteStroke(strokeId)
    }

    private suspend fun touch(noteId: Long) {
        val n = dao.getNote(noteId) ?: return
        dao.updateNote(n.copy(updatedAt = System.currentTimeMillis()))
    }
}

fun StrokeEntity.toStroke(): Stroke = Stroke(
    id = id,
    noteId = noteId,
    pageIndex = pageIndex,
    penType = PenType.valueOf(penType),
    colorArgb = colorArgb,
    width = width,
    points = StrokeSerializer.deserialize(points)
)

fun Stroke.toEntity(noteId: Long, pageIndex: Int): StrokeEntity = StrokeEntity(
    id = id,
    noteId = noteId,
    pageIndex = pageIndex,
    penType = penType.name,
    colorArgb = colorArgb,
    width = width,
    points = StrokeSerializer.serialize(points)
)
