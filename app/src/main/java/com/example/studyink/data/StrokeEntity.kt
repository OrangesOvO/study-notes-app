package com.example.studyink.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "strokes",
    indices = [Index(value = ["noteId", "pageIndex"])]
)
data class StrokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val noteId: Long,
    val pageIndex: Int,
    val penType: String,   // PenType.name
    val colorArgb: Int,
    val width: Float,
    val points: String,    // StrokeSerializer 序列化结果
    val createdAt: Long = System.currentTimeMillis()
)
