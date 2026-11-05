package com.example.studyink.model

/** 单个采样点：逻辑画布坐标（非屏幕坐标） */
data class InkPoint(
    val x: Float,
    val y: Float,
    val t: Long,        // 时间戳（毫秒）
    val pressure: Float  // 压力 0..1
)
