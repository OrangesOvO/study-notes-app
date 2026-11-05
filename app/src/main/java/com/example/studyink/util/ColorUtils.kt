package com.example.studyink.util

import androidx.compose.ui.graphics.Color

/** 把 ARGB Int 转成 Compose Color */
fun intToColor(argb: Int): Color {
    val a = (argb ushr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    return Color(r / 255f, g / 255f, b / 255f, a / 255f)
}
