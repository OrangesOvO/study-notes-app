package com.example.studyink.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.example.studyink.model.InkPoint
import com.example.studyink.model.PageBackground
import com.example.studyink.model.PenType
import com.example.studyink.model.Stroke
import com.example.studyink.model.Tool
import com.example.studyink.util.intToColor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 页面外的底色 */
private val BackdropGray = Color(0xFFECEEF2)

/** 页面边框 */
private val PageBorder = Color(0xFFD3D7DE)

/** 页面投影 */
private val PageShadow = Color(0x26000000)

private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 5f

/**
 * 自绘画布：渲染「页面底图 + 底纹 + 已保存笔迹 + 正在书写的笔迹」。
 *
 * 坐标约定：
 *  - 笔迹坐标一律是逻辑画布坐标（0..canvasWidth, 0..canvasHeight）；
 *  - 屏幕 <-> 逻辑 的换算完全交给 [PageViewport]，绘制与触摸共用同一份；
 *  - 页面在可视区内居中，四周露出灰底，形成明确的「纸张」边界。
 *
 * 手势约定：
 *  - 单指 / 单笔书写；只跟踪第一根按下的指针；
 *  - 出现第二根指针即切入缩放 / 平移，并丢弃当前未完成笔画；
 *  - stylusOnly 为 true 时，只有触控笔（[PointerType.Stylus] / [PointerType.Eraser]）能落笔。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrawingCanvas(
    canvasWidth: Int,
    canvasHeight: Int,
    background: PageBackground,
    baseBitmap: Bitmap?,
    strokes: List<Stroke>,
    tool: Tool,
    penColor: Color,
    penWidth: Float,
    stylusOnly: Boolean,
    onStrokeEnd: (Stroke) -> Unit,
    onEraseStroke: (Stroke) -> Unit,
    onFingerRejected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentStroke = remember { mutableStateOf<Stroke?>(null) }
    val currentOnStrokeEnd by rememberUpdatedState(onStrokeEnd)
    val currentOnErase by rememberUpdatedState(onEraseStroke)
    val currentOnFingerRejected by rememberUpdatedState(onFingerRejected)
    val currentStrokes by rememberUpdatedState(strokes)

    val viewSize = remember { mutableStateOf(Size.Zero) }
    val zoom = remember { mutableStateOf(1f) }
    val pan = remember { mutableStateOf(Offset.Zero) }

    val canvasWf = canvasWidth.toFloat()
    val canvasHf = canvasHeight.toFloat()

    // 换页 / 换画布尺寸时复位视口
    LaunchedEffect(canvasWidth, canvasHeight) {
        zoom.value = 1f
        pan.value = Offset.Zero
    }

    val viewport = PageViewport.compute(
        viewW = viewSize.value.width,
        viewH = viewSize.value.height,
        canvasW = canvasWf,
        canvasH = canvasHf,
        zoom = zoom.value,
        pan = pan.value
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { viewSize.value = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(canvasWf, canvasHf, tool, penColor, penWidth, stylusOnly) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val stylusDown = first.type == PointerType.Stylus ||
                            first.type == PointerType.Eraser
                        val drawAllowed = !stylusOnly || stylusDown
                        if (stylusOnly && !stylusDown) currentOnFingerRejected()

                        val points = ArrayList<InkPoint>()
                        var shapeStart: Offset? = null
                        var transforming = false
                        val erasedIds = HashSet<Long>()
                        val activeId: PointerId = first.id

                        // 起始点
                        if (drawAllowed) {
                            val vp = viewportNow(viewSize, zoom, pan, canvasWf, canvasHf)
                            val p = vp.toLogical(first.position.x, first.position.y)
                            val t = System.currentTimeMillis()
                            when (tool) {
                                Tool.ERASER -> eraseAt(
                                    p, erasedIds, currentStrokes, penWidth * 2f, currentOnErase
                                )

                                Tool.LINE, Tool.RECT, Tool.OVAL -> shapeStart = p

                                else -> {
                                    points.add(
                                        InkPoint(p.x, p.y, t, first.pressure.coerceIn(0f, 1f))
                                    )
                                    currentStroke.value = makeStroke(tool, points, penColor, penWidth)
                                }
                            }
                        }
                        first.consume()

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }

                            // 多指 -> 缩放 / 平移
                            if (pressedCount >= 2) {
                                if (!transforming) {
                                    transforming = true
                                    currentStroke.value = null
                                    shapeStart = null
                                    points.clear()
                                }
                                applyTransform(event, viewSize, zoom, pan, canvasWf, canvasHf)
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            // 全部抬起 -> 收尾
                            if (pressedCount == 0) {
                                if (drawAllowed && !transforming) {
                                    val up = event.changes.firstOrNull { it.changedToUp() }
                                        ?: event.changes.firstOrNull()
                                    if (up != null) {
                                        val vp = viewportNow(viewSize, zoom, pan, canvasWf, canvasHf)
                                        val p = vp.toLogical(up.position.x, up.position.y)
                                        val t = System.currentTimeMillis()
                                        when (tool) {
                                            Tool.ERASER -> Unit

                                            Tool.LINE, Tool.RECT, Tool.OVAL -> {
                                                val s = shapeStart
                                                if (s != null && (p - s).getDistance() >= 4f) {
                                                    currentOnStrokeEnd(
                                                        makeStroke(
                                                            tool,
                                                            listOf(
                                                                InkPoint(s.x, s.y, t, 1f),
                                                                InkPoint(p.x, p.y, t, 1f)
                                                            ),
                                                            penColor, penWidth
                                                        )
                                                    )
                                                }
                                            }

                                            else -> {
                                                points.add(
                                                    InkPoint(
                                                        p.x, p.y, t,
                                                        up.pressure.coerceIn(0f, 1f)
                                                    )
                                                )
                                                if (points.size >= 2) {
                                                    currentOnStrokeEnd(
                                                        makeStroke(tool, points, penColor, penWidth)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                currentStroke.value = null
                                break
                            }

                            if (transforming) {
                                applyTransform(event, viewSize, zoom, pan, canvasWf, canvasHf)
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            val change = event.changes.firstOrNull { it.id == activeId }
                            if (change == null) {
                                currentStroke.value = null
                                break
                            }
                            if (!drawAllowed) {
                                change.consume()
                                continue
                            }

                            val vp = viewportNow(viewSize, zoom, pan, canvasWf, canvasHf)
                            val p = vp.toLogical(change.position.x, change.position.y)
                            val t = System.currentTimeMillis()
                            val pr = change.pressure.coerceIn(0f, 1f)

                            when (tool) {
                                Tool.ERASER -> eraseAt(
                                    p, erasedIds, currentStrokes, penWidth * 2f, currentOnErase
                                )

                                Tool.LINE, Tool.RECT, Tool.OVAL -> {
                                    val s = shapeStart
                                    if (s != null) {
                                        currentStroke.value = makeStroke(
                                            tool,
                                            listOf(
                                                InkPoint(s.x, s.y, t, 1f),
                                                InkPoint(p.x, p.y, t, 1f)
                                            ),
                                            penColor, penWidth
                                        )
                                    }
                                }

                                else -> {
                                    points.add(InkPoint(p.x, p.y, t, pr))
                                    currentStroke.value = makeStroke(tool, points, penColor, penWidth)
                                }
                            }
                            change.consume()
                        }

                        currentStroke.value = null
                    }
                }
        ) {
            drawRect(BackdropGray)
            if (viewport.scale <= 0f) return@Canvas

            // 投影（屏幕坐标系）
            drawRect(
                color = PageShadow,
                topLeft = Offset(viewport.offsetX + 4f, viewport.offsetY + 6f),
                size = Size(viewport.pageWidthPx, viewport.pageHeightPx)
            )

            withTransform({
                translate(viewport.offsetX, viewport.offsetY)
                // 必须显式 pivot = Offset.Zero：默认是画布中心，会导致页面偏右下
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
                        drawPageBackground(background, viewport.canvasW, viewport.canvasH)
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

        if (zoom.value != 1f) {
            TextButton(
                onClick = {
                    zoom.value = 1f
                    pan.value = Offset.Zero
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Text("复位")
            }
        }
    }
}

// ---- 手势工具函数 ----

private fun viewportNow(
    viewSize: MutableState<Size>,
    zoom: MutableState<Float>,
    pan: MutableState<Offset>,
    canvasW: Float,
    canvasH: Float
): PageViewport = PageViewport.compute(
    viewW = viewSize.value.width,
    viewH = viewSize.value.height,
    canvasW = canvasW,
    canvasH = canvasH,
    zoom = zoom.value,
    pan = pan.value
)

private fun applyTransform(
    event: PointerEvent,
    viewSize: MutableState<Size>,
    zoom: MutableState<Float>,
    pan: MutableState<Offset>,
    canvasW: Float,
    canvasH: Float
) {
    val cur = event.averageCurrent()
    val prev = event.averagePrevious()
    if (cur == Offset.Unspecified || prev == Offset.Unspecified) return

    val viewW = viewSize.value.width
    val viewH = viewSize.value.height
    if (viewW <= 0f || viewH <= 0f) return

    val zoomDelta = event.zoomFactor()

    val vp = PageViewport.compute(viewW, viewH, canvasW, canvasH, zoom.value, pan.value)
    // 以「上一帧质心」对应的逻辑点为锚，内容才与手指同步。
    // 用当前质心会额外引入一次平移，双指拖动会走两倍距离。
    val logical = vp.toLogical(prev.x, prev.y)

    val base = min(viewW / canvasW, viewH / canvasH)
    val newZoom = (zoom.value * zoomDelta).coerceIn(MIN_ZOOM, MAX_ZOOM)
    val newScale = base * newZoom
    val centeredX = (viewW - canvasW * newScale) / 2f
    val centeredY = (viewH - canvasH * newScale) / 2f

    val newPanX = cur.x - logical.x * newScale - centeredX
    val newPanY = cur.y - logical.y * newScale - centeredY

    zoom.value = newZoom
    pan.value = clampPan(newPanX, newPanY, viewW, viewH, canvasW, canvasH, newScale)
}

private fun clampPan(
    px: Float,
    py: Float,
    viewW: Float,
    viewH: Float,
    canvasW: Float,
    canvasH: Float,
    scale: Float
): Offset {
    val pageW = canvasW * scale
    val pageH = canvasH * scale
    val limitX = max((pageW - viewW) / 2f, 0f) + 96f
    val limitY = max((pageH - viewH) / 2f, 0f) + 96f
    return Offset(px.coerceIn(-limitX, limitX), py.coerceIn(-limitY, limitY))
}

private fun PointerEvent.averageCurrent(): Offset {
    var x = 0f
    var y = 0f
    var n = 0
    changes.forEach {
        if (it.pressed) {
            x += it.position.x
            y += it.position.y
            n++
        }
    }
    if (n == 0) return Offset.Unspecified
    return Offset(x / n, y / n)
}

private fun PointerEvent.averagePrevious(): Offset {
    var x = 0f
    var y = 0f
    var n = 0
    changes.forEach {
        if (it.pressed) {
            x += it.previousPosition.x
            y += it.previousPosition.y
            n++
        }
    }
    if (n == 0) return Offset.Unspecified
    return Offset(x / n, y / n)
}

private fun PointerEvent.zoomFactor(): Float {
    val pressed = changes.filter { it.pressed }
    if (pressed.size < 2) return 1f
    val c = averageCurrent()
    val pc = averagePrevious()
    if (c == Offset.Unspecified || pc == Offset.Unspecified) return 1f
    var cur = 0f
    var prev = 0f
    pressed.forEach {
        cur += (it.position - c).getDistance()
        prev += (it.previousPosition - pc).getDistance()
    }
    return if (prev < 1f) 1f else (cur / prev).coerceIn(0.5f, 2f)
}

private fun makeStroke(
    tool: Tool,
    points: List<InkPoint>,
    color: Color,
    width: Float
): Stroke = Stroke(
    penType = tool.penTypeOrNull ?: PenType.PEN,
    colorArgb = color.toArgb(),
    width = width,
    points = points
)

private fun eraseAt(
    p: Offset,
    erasedIds: MutableSet<Long>,
    strokes: List<Stroke>,
    radius: Float,
    onErase: (Stroke) -> Unit
) {
    strokes.forEach { s ->
        if (s.id in erasedIds) return@forEach
        if (s.hitTest(p, radius)) {
            erasedIds.add(s.id)
            onErase(s)
        }
    }
}

private fun Stroke.hitTest(p: Offset, radius: Float): Boolean {
    if (points.isEmpty()) return false
    val r = radius + width / 2f
    val r2 = r * r
    if (points.size == 1) {
        val dx = points[0].x - p.x
        val dy = points[0].y - p.y
        return dx * dx + dy * dy <= r2
    }
    for (i in 0 until points.size - 1) {
        val d = distanceToSegment(
            p,
            Offset(points[i].x, points[i].y),
            Offset(points[i + 1].x, points[i + 1].y)
        )
        if (d <= r) return true
    }
    return false
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val ab = b - a
    val len2 = ab.x * ab.x + ab.y * ab.y
    if (len2 <= 0.0001f) return (p - a).getDistance()
    val t = (((p.x - a.x) * ab.x + (p.y - a.y) * ab.y) / len2).coerceIn(0f, 1f)
    val proj = Offset(a.x + ab.x * t, a.y + ab.y * t)
    return (p - proj).getDistance()
}

// ---- 绘制 ----

private fun DrawScope.drawStroke(stroke: Stroke) {
    val points = stroke.points
    if (points.isEmpty()) return
    val color = intToColor(stroke.colorArgb)

    val isShape = stroke.penType == PenType.LINE ||
        stroke.penType == PenType.RECT ||
        stroke.penType == PenType.OVAL

    if (points.size == 1 && !isShape) {
        val p = points[0]
        val radius = stroke.width * (0.4f + 0.6f * p.pressure) / 2f
        drawCircle(color = color, radius = radius, center = Offset(p.x, p.y))
        return
    }

    when (stroke.penType) {
        PenType.LINE -> {
            if (points.size < 2) return
            val a = points.first()
            val b = points.last()
            drawLine(
                color = color,
                start = Offset(a.x, a.y),
                end = Offset(b.x, b.y),
                strokeWidth = stroke.width,
                cap = StrokeCap.Round
            )
        }

        PenType.RECT -> {
            if (points.size < 2) return
            val a = points.first()
            val b = points.last()
            drawRect(
                color = color,
                topLeft = Offset(min(a.x, b.x), min(a.y, b.y)),
                size = Size(abs(b.x - a.x), abs(b.y - a.y)),
                style = DrawStroke(width = stroke.width)
            )
        }

        PenType.OVAL -> {
            if (points.size < 2) return
            val a = points.first()
            val b = points.last()
            drawOval(
                color = color,
                topLeft = Offset(min(a.x, b.x), min(a.y, b.y)),
                size = Size(abs(b.x - a.x), abs(b.y - a.y)),
                style = DrawStroke(width = stroke.width)
            )
        }

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
