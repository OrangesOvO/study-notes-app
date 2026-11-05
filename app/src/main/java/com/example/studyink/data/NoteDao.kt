package com.example.studyink.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeNote(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getNote(id: Long): NoteEntity?

    @Insert
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("SELECT * FROM strokes WHERE noteId = :noteId AND pageIndex = :pageIndex ORDER BY createdAt ASC, id ASC")
    fun observeStrokes(noteId: Long, pageIndex: Int): Flow<List<StrokeEntity>>

    @Query("SELECT * FROM strokes WHERE noteId = :noteId ORDER BY pageIndex ASC, createdAt ASC, id ASC")
    suspend fun getAllStrokesForNote(noteId: Long): List<StrokeEntity>

    @Insert
    suspend fun insertStroke(stroke: StrokeEntity): Long

    @Query("DELETE FROM strokes WHERE id = :strokeId")
    suspend fun deleteStroke(strokeId: Long)

    @Query("DELETE FROM strokes WHERE noteId = :noteId")
    suspend fun deleteStrokesForNote(noteId: Long)

    // ---- 多页管理 ----

    @Query("UPDATE notes SET pageCount = pageCount + 1, updatedAt = :ts WHERE id = :id")
    suspend fun incrementPageCount(id: Long, ts: Long)

    @Query("UPDATE notes SET pageCount = pageCount - 1, updatedAt = :ts WHERE id = :id AND pageCount > 1")
    suspend fun decrementPageCount(id: Long, ts: Long)

    @Query("DELETE FROM strokes WHERE noteId = :noteId AND pageIndex = :pageIndex")
    suspend fun deleteStrokesOnPage(noteId: Long, pageIndex: Int)

    @Query("UPDATE strokes SET pageIndex = pageIndex - 1 WHERE noteId = :noteId AND pageIndex > :pageIndex")
    suspend fun shiftStrokesDown(noteId: Long, pageIndex: Int)

    // ---- 标题与底纹 ----

    @Query("UPDATE notes SET title = :title, updatedAt = :ts WHERE id = :id")
    suspend fun renameNote(id: Long, title: String, ts: Long)

    @Query("UPDATE notes SET background = :background, updatedAt = :ts WHERE id = :id")
    suspend fun setBackground(id: Long, background: String, ts: Long)
}
