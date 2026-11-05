package com.example.studyink.ui.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.studyink.model.CanvasSpec
import com.example.studyink.model.PageBackground

private val PatternColor = Color(0xFFD2D8E0)
private const val LINE_WIDTH = 1.4f

/** 在逻辑画布坐标系内绘制页面底纹（0..width, 0..height） */
fun DrawScope.drawPageBackground(background: PageBackground, width: Float, height: Float) {
    val spacing = CanvasSpec.PATTERN_SPACING

    when (background) {
        PageBackground.BLANK -> Unit

        PageBackground.LINED -> {
            var y = spacing
            while (y < height) {
                drawLine(PatternColor, Offset(0f, y), Offset(width, y), strokeWidth = LINE_WIDTH)
                y += spacing
            }
        }

        PageBackground.GRID -> {
            var y = spacing
            while (y < height) {
                drawLine(PatternColor, Offset(0f, y), Offset(width, y), strokeWidth = LINE_WIDTH)
                y += spacing
            }
            var x = spacing
            while (x < width) {
                drawLine(PatternColor, Offset(x, 0f), Offset(x, height), strokeWidth = LINE_WIDTH)
                x += spacing
            }
        }

        PageBackground.DOTS -> {
            var y = spacing
            while (y < height) {
                var x = spacing
                while (x < width) {
                    drawCircle(PatternColor, radius = 2.2f, center = Offset(x, y))
                    x += spacing
                }
                y += spacing
            }
        }

        PageBackground.TIAN -> {
            val cell = CanvasSpec.TIAN_CELL
            val half = cell / 2f
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            var y = 0f
            while (y < height) {
                var x = 0f
                while (x < width) {
                    drawRect(
                        color = PatternColor,
                        topLeft = Offset(x, y),
                        size = Size(cell, cell),
                        style = Stroke(width = LINE_WIDTH)
                    )
                    drawLine(
                        PatternColor,
                        Offset(x + half, y),
                        Offset(x + half, y + cell),
                        strokeWidth = 1f,
                        pathEffect = dash
                    )
                    drawLine(
                        PatternColor,
                        Offset(x, y + half),
                        Offset(x + cell, y + half),
                        strokeWidth = 1f,
                        pathEffect = dash
                    )
                    x += cell
                }
                y += cell
            }
        }
    }
}
