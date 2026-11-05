package com.example.studyink.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import com.example.studyink.model.InkPoint
import com.example.studyink.model.PenType
import com.example.studyink.model.Stroke
import com.example.studyink.util.intToColor
import kotlin.math.min

/**
 * 自绘画布：渲染底图（PDF 页位图或白底）+ 已保存笔迹 + 正在书写的笔迹。
 * 指针输入在这里捕获（含压感），屏幕坐标换算成逻辑画布坐标。
 */
@Composable
fun DrawingCanvas(
    canvasWidth: Int,
    canvasHeight: Int,
    baseBitmap: Bitmap?,
    strokes: List<Stroke>,
    penType: PenType,
    penColor: Color,
    penWidth: Float,
    onStrokeEnd: (Stroke) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentStroke = remember { mutableStateOf<Stroke?>(null) }
    val currentOnStrokeEnd by rememberUpdatedState(onStrokeEnd)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(canvasWidth, canvasHeight, penType, penColor, penWidth) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue

                        val scale = min(
                            size.width.toFloat() / canvasWidth,
                            size.height.toFloat() / canvasHeight
                        )
                        val offsetX = (size.width - canvasWidth * scale) / 2f
                        val offsetY = (size.height - canvasHeight * scale) / 2f
                        val lx = (change.position.x - offsetX) / scale
                        val ly = (change.position.y - offsetY) / scale
                        val now = System.currentTimeMillis()
                        val pressure = change.pressure.coerceIn(0f, 1f)

                        when {
                            change.changedToDown() -> {
                                currentStroke.value = Stroke(
                                    noteId = 0L,
                                    pageIndex = 0,
                                    penType = penType,
                                    colorArgb = penColor.toArgb(),
                                    width = penWidth,
                                    points = listOf(InkPoint(lx, ly, now, pressure))
                                )
                            }

                            change.changedToUp() -> {
                                currentStroke.value?.let { s ->
                                    val final = s.copy(
                                        points = s.points + InkPoint(lx, ly, now, pressure)
                                    )
                                    currentOnStrokeEnd(final)
                                    currentStroke.value = null
                                }
                            }

                            else -> {
                                currentStroke.value = currentStroke.value?.let { s ->
                                    s.copy(points = s.points + InkPoint(lx, ly, now, pressure))
                                }
                            }
                        }
                        change.consume()
                    }
                }
            }
    ) {
        val scale = min(size.width / canvasWidth, size.height / canvasHeight)
        val offsetX = (size.width - canvasWidth * scale) / 2f
        val offsetY = (size.height - canvasHeight * scale) / 2f

        translate(offsetX, offsetY) {
            scale(scale, scale) {
                if (baseBitmap != null) {
                    drawImage(baseBitmap.asImageBitmap())
                } else {
                    drawRect(Color.White)
                }
                strokes.forEach { drawStroke(it) }
                currentStroke.value?.let { drawStroke(it) }
            }
        }
    }
}

private fun DrawScope.drawStroke(stroke: Stroke) {
    val points = stroke.points
    if (points.isEmpty()) return
    val color = intToColor(stroke.colorArgb)

    if (points.size == 1) {
        val p = points[0]
        val radius = stroke.width * (0.4f + 0.6f * p.pressure) / 2f
        drawCircle(color = color, radius = radius, center = Offset(p.x, p.y))
        return
    }

    when (stroke.penType) {
        PenType.PEN -> {
            val base = stroke.width
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val avg = (a.pressure + b.pressure) / 2f
                val w = base * (0.4f + 0.6f * avg)
                drawLine(
                    color = color,
                    start = Offset(a.x, a.y),
                    end = Offset(b.x, b.y),
                    strokeWidth = w,
                    cap = StrokeCap.Round
                )
            }
        }

        PenType.MARKER -> {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            drawPath(
                path = path,
                color = color.copy(alpha = 0.9f),
                style = DrawStroke(width = stroke.width, cap = StrokeCap.Round)
            )
        }

        PenType.HIGHLIGHTER -> {
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
            }
            drawPath(
                path = path,
                color = color.copy(alpha = 0.35f),
                style = DrawStroke(width = stroke.width, cap = StrokeCap.Square)
            )
        }
    }
}
