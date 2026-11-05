package com.example.studyink.model

/** 一条完整笔迹 */
data class Stroke(
    val id: Long = 0L,
    val noteId: Long = 0L,
    val pageIndex: Int = 0,
    val penType: PenType,
    val colorArgb: Int,   // ARGB 打包的 Int
    val width: Float,     // 基础笔宽（逻辑画布像素）
    val points: List<InkPoint>
)
