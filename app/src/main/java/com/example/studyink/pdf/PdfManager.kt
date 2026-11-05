package com.example.studyink.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.example.studyink.model.CanvasSpec
import java.io.File
import java.util.UUID

/**
 * PDF 导入与页面渲染。基于 Android 内置 PdfRenderer，无第三方依赖。
 */
object PdfManager {

    data class ImportResult(val filePath: String, val pageCount: Int)

    /** 把用户选中的 PDF 复制到应用私有目录，并返回路径与页数 */
    fun importPdf(context: Context, uri: Uri): ImportResult {
        val dir = File(context.filesDir, "pdfs").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.pdf")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法读取所选文件")

        val pageCount = countPages(dest)
        if (pageCount <= 0) {
            dest.delete()
            throw IllegalStateException("无法解析该 PDF 文件")
        }
        return ImportResult(dest.absolutePath, pageCount)
    }

    /** 获取导入文件的展示名（用于默认标题） */
    fun displayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) {
                c.getString(idx)?.let { if (it.isNotBlank()) return it }
            }
        }
        return uri.lastPathSegment ?: "导入的PDF"
    }

    fun countPages(file: File): Int {
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        return try {
            renderer.pageCount
        } finally {
            renderer.close()
            fd.close()
        }
    }

    /** 获取某页的尺寸（PDF 点单位，1 点 = 1/72 英寸） */
    fun pageSizePoints(pdfPath: String, pageIndex: Int): Pair<Int, Int>? {
        val file = File(pdfPath)
        if (!file.exists()) return null
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        return try {
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                null
            } else {
                val page = renderer.openPage(pageIndex)
                try {
                    page.width to page.height
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            fd.close()
        }
    }

    /** 以精确尺寸渲染某页位图（用于显示与导出，aspect 保持一致） */
    fun renderPageExact(pdfPath: String, pageIndex: Int, width: Int, height: Int): Bitmap? {
        val file = File(pdfPath)
        if (!file.exists()) return null
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        return try {
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) {
                null
            } else {
                val page = renderer.openPage(pageIndex)
                try {
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val scale = width.toFloat() / page.width
                    val matrix = Matrix().apply { setScale(scale, scale) }
                    page.render(bmp, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                } finally {
                    page.close()
                }
            }
        } finally {
            renderer.close()
            fd.close()
        }
    }

    /** 计算某一页的逻辑画布尺寸（笔迹坐标系） */
    fun canvasSizeFor(type: String, pdfPath: String?, pageIndex: Int): Pair<Int, Int> {
        if (type == "PDF" && pdfPath != null) {
            val (pw, ph) = pageSizePoints(pdfPath, pageIndex)
                ?: return CanvasSpec.A4_WIDTH to CanvasSpec.A4_HEIGHT
            return (pw * CanvasSpec.PDF_RENDER_SCALE) to (ph * CanvasSpec.PDF_RENDER_SCALE)
        }
        return CanvasSpec.A4_WIDTH to CanvasSpec.A4_HEIGHT
    }
}
