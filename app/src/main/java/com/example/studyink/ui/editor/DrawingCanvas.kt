package com.example.studyink.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import com.example.studyink.model.InkPoint
import com.example.studyink.model.PenType
import com.example.studyink.model.Stroke
import com.example.studyink.util.intToColor

/** 页面外的底色 */
private val BackdropGray = Color(0xFFECEEF2)

/** 页面边框 */
private val PageBorder = Color(0xFFD3D7DE)

/** 页面投影 */
private val PageShadow = Color(0x26000000)

/**
 * 自绘画布：渲染「页面底图 + 已保存笔迹 + 正在书写的笔迹」。
 *
 * 坐标约定：
 *  - 笔迹坐标一律是逻辑画布坐标（0..canvasWidth, 0..canvasHeight）；
 *  - 屏幕 <-> 逻辑 的换算完全交给 [PageViewport]，绘制与触摸共用同一份，
 *    所以不会出现笔迹和笔尖错位；
 *  - 页面在可视区内水平 + 垂直居中，四周露出灰底，形成明确的「纸张」边界。
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

    // 可视区尺寸。放在 state 里，页面逻辑尺寸（PDF 翻页）变化时也能同步重算视口。
    val viewSize = remember { mutableStateOf(Size.Zero) }
    val viewport = PageViewport.compute(
        viewW = viewSize.value.width,
        viewH = viewSize.value.height,
        canvasW = canvasWidth.toFloat(),
        canvasH = canvasHeight.toFloat()
    )
    // 手势回调里读最新视口，避免捕获到旧值
    val latestViewport by rememberUpdatedState(viewport)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { sz ->
                viewSize.value = Size(sz.width.toFloat(), sz.height.toFloat())
            }
            .pointerInput(canvasWidth, canvasHeight, penType, penColor, penWidth) {
                // 只跟踪第一根按下的指针；掌触/多指带来的其它指针全部忽略。
                var activeId: PointerId? = null

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val vp = latestViewport
                        val active = activeId

                        if (active == null) {
                            val down = event.changes.firstOrNull { it.changedToDown() }
                                ?: continue
                            activeId = down.id
                            val p = vp.toLogical(down.position.x, down.position.y)
                            currentStroke.value = Stroke(
                                noteId = 0L,
                                pageIndex = 0,
                                penType = penType,
                                colorArgb = penColor.toArgb(),
                                width = penWidth,
                                points = listOf(
                                    InkPoint(
                                        x = p.x,
                                        y = p.y,
                                        t = System.currentTimeMillis(),
                                        pressure = down.pressure.coerceIn(0f, 1f)
                                    )
                                )
                            )
                            down.consume()
                            continue
                        }

                        val index = event.changes.indexOfFirst { it.id == active }
                        if (index < 0) {
                            // 指针被系统取消，丢弃这一笔
                            currentStroke.value = null
                            activeId = null
                            continue
                        }

                        val change = event.changes[index]
                        val p = vp.toLogical(change.position.x, change.position.y)
                        val now = System.currentTimeMillis()
                        val pressure = change.pressure.coerceIn(0f, 1f)

                        if (change.changedToUp()) {
                            currentStroke.value?.let { s ->
                                currentOnStrokeEnd(
                                    s.copy(points = s.points + InkPoint(p.x, p.y, now, pressure))
                                )
                            }
                            currentStroke.value = null
                            activeId = null
                        } else {
                            currentStroke.value = currentStroke.value?.let { s ->
                                s.copy(points = s.points + InkPoint(p.x, p.y, now, pressure))
                            }
                        }
                        change.consume()
                    }
                }
            }
    ) {
        drawRect(BackdropGray)
        if (viewport.scale <= 0f) return@Canvas

        val pageW = viewport.pageWidthPx
        val pageH = viewport.pageHeightPx

        // 投影（屏幕坐标系，不参与缩放）
        drawRect(
            color = PageShadow,
            topLeft = Offset(viewport.offsetX + 4f, viewport.offsetY + 6f),
            size = Size(pageW, pageH)
        )

        withTransform({
            translate(viewport.offsetX, viewport.offsetY)
            // 注意：pivot 必须显式传 Offset.Zero。
            // DrawTransform.scale 默认以画布中心为轴，会导致页面整体偏向右下。
            scale(viewport.scale, viewport.scale, pivot = Offset.Zero)
        }) {
            clipRect(0f, 0f, viewport.canvasW, viewport.canvasH) {
                if (baseBitmap != null) {
                    drawImage(baseBitmap.asImageBitmap())
                } else {
                    drawRect(
                        color = Color.White,
                        topLeft = Offset.Zero,
                        size = Size(viewport.canvasW, viewport.canvasH)
                    )
                }
                strokes.forEach { drawStroke(it) }
                currentStroke.value?.let { drawStroke(it) }
            }

            // 页面边框：1 物理像素
            drawRect(
                color = PageBorder,
                topLeft = Offset.Zero,
                size = Size(viewport.canvasW, viewport.canvasH),
                style = DrawStroke(width = 1f / viewport.scale)
            )
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
