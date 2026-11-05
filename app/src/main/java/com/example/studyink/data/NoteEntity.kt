package com.example.studyink.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val type: String,                 // NoteType.name
    val pdfPath: String? = null,      // 导入的 PDF 私有路径，BLANK 时为 null
    val currentPage: Int = 0,
    val pageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
