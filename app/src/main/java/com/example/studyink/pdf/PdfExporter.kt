package com.example.studyink.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.example.studyink.data.NoteEntity
import com.example.studyink.model.CanvasSpec
import com.example.studyink.model.NoteType
import com.example.studyink.model.PageBackground
import com.example.studyink.model.PenType
import com.example.studyink.model.Stroke
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 把「底图 / 底纹 + 笔迹」合成导出为新 PDF。
 * 导出页为栅格化页面，个人学习用途足够。
 */
object PdfExporter {

    suspend fun export(
        context: Context,
        note: NoteEntity,
        strokesByPage: Map<Int, List<Stroke>>,
        targetUri: Uri
    ): Boolean = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val background = parseBackground(note.background)
        try {
            for (pageIdx in 0 until note.pageCount) {
                val (cw, ch) = PdfManager.canvasSizeFor(note.type, note.pdfPath, pageIdx)
                val bmp = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                canvas.drawColor(Color.WHITE)

                if (note.type == NoteType.PDF.name && note.pdfPath != null) {
                    PdfManager.renderPageExact(note.pdfPath, pageIdx, cw, ch)?.let { base ->
                        canvas.drawBitmap(base, 0f, 0f, null)
                        base.recycle()
                    }
                } else {
                    drawBackground(canvas, background, cw, ch)
                }

                strokesByPage[pageIdx]?.forEach { stroke ->
                    drawStroke(canvas, stroke)
                }

                val pageInfo = PdfDocument.PageInfo.Builder(cw, ch, pageIdx + 1).create()
                val page = document.startPage(pageInfo)
                page.canvas.drawBitmap(bmp, 0f, 0f, null)
                document.finishPage(page)
                bmp.recycle()
            }

            context.contentResolver.openOutputStream(targetUri)?.use { out ->
                document.writeTo(out)
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        } finally {
            document.close()
        }
    }

    private fun parseBackground(name: String?): PageBackground =
        PageBackground.values().firstOrNull { it.name == name } ?: PageBackground.BLANK

    private fun drawBackground(canvas: Canvas, bg: PageBackground, w: Int, h: Int) {
        if (bg == PageBackground.BLANK) return
        val wf = w.toFloat()
        val hf = h.toFloat()
        val line = Paint().apply {
            isAntiAlias = true
            color = PATTERN_COLOR
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }
        val spacing = CanvasSpec.PATTERN_SPACING

        when (bg) {
            PageBackground.BLANK -> Unit

            PageBackground.LINED -> {
                var y = spacing
                while (y < hf) {
                    canvas.drawLine(0f, y, wf, y, line)
                    y += spacing
                }
            }

            PageBackground.GRID -> {
                var y = spacing
                while (y < hf) {
                    canvas.drawLine(0f, y, wf, y, line)
                    y += spacing
                }
                var x = spacing
                while (x < wf) {
                    canvas.drawLine(x, 0f, x, hf, line)
                    x += spacing
                }
            }

            PageBackground.DOTS -> {
                val dot = Paint().apply {
                    isAntiAlias = true
                    color = PATTERN_COLOR
                    style = Paint.Style.FILL
                }
                var y = spacing
                while (y < hf) {
                    var x = spacing
                    while (x < wf) {
                        canvas.drawCircle(x, y, 2.2f, dot)
                        x += spacing
                    }
                    y += spacing
                }
            }

            PageBackground.TIAN -> {
                val cell = CanvasSpec.TIAN_CELL
                val half = cell / 2f
                val dash = Paint(line).apply {
                    strokeWidth = 1f
                    pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
                }
                var y = 0f
                while (y < hf) {
                    var x = 0f
                    while (x < wf) {
                        canvas.drawRect(x, y, x + cell, y + cell, line)
                        canvas.drawLine(x + half, y, x + half, y + cell, dash)
                        canvas.drawLine(x, y + half, x + cell, y + half, dash)
                        x += cell
                    }
                    y += cell
                }
            }
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: Stroke) {
        val points = stroke.points
        if (points.isEmpty()) return

        val isShape = stroke.penType == PenType.LINE ||
            stroke.penType == PenType.RECT ||
            stroke.penType == PenType.OVAL

        if (points.size == 1 && !isShape) {
            val p = points[0]
            val paint = Paint().apply {
                isAntiAlias = true
                color = stroke.colorArgb
                style = Paint.Style.FILL
            }
            canvas.drawCircle(p.x, p.y, stroke.width / 2f, paint)
            return
        }

        when (stroke.penType) {
            PenType.LINE -> {
                if (points.size < 2) return
                val a = points.first()
                val b = points.last()
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = stroke.colorArgb
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = stroke.width
                }
                canvas.drawLine(a.x, a.y, b.x, b.y, paint)
            }

            PenType.RECT -> {
                if (points.size < 2) return
                val a = points.first()
                val b = points.last()
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = stroke.colorArgb
                    style = Paint.Style.STROKE
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = stroke.width
                }
                canvas.drawRect(
                    min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y), paint
                )
            }

            PenType.OVAL -> {
                if (points.size < 2) return
                val a = points.first()
                val b = points.last()
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = stroke.colorArgb
                    style = Paint.Style.STROKE
                    strokeWidth = stroke.width
                }
                canvas.drawOval(
                    RectF(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y)), paint
                )
            }

            PenType.PEN -> {
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = stroke.colorArgb
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                for (i in 0 until points.size - 1) {
                    val a = points[i]
                    val b = points[i + 1]
                    val avgPressure = (a.pressure + b.pressure) / 2f
                    paint.strokeWidth = stroke.width * (0.4f + 0.6f * avgPressure)
                    canvas.drawLine(a.x, a.y, b.x, b.y, paint)
                }
            }

            PenType.MARKER -> {
                val path = Path()
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = withAlpha(stroke.colorArgb, 0xE6) // ~90% 不透明
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    strokeWidth = stroke.width
                }
                canvas.drawPath(path, paint)
            }

            PenType.HIGHLIGHTER -> {
                val path = Path()
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
                val paint = Paint().apply {
                    isAntiAlias = true
                    color = withAlpha(stroke.colorArgb, 0x59) // ~35% 不透明
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.SQUARE
                    strokeJoin = Paint.Join.BEVEL
                    strokeWidth = stroke.width
                }
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun withAlpha(argb: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(argb), Color.green(argb), Color.blue(argb))

    private val PATTERN_COLOR = 0xFFD2D8E0.toInt()
}
