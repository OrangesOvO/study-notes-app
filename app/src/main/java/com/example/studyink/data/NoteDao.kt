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
}
