package com.example.studyink.ui.editor

import androidx.compose.ui.geometry.Offset
import kotlin.math.min

/**
 * 页面视口变换：把「逻辑画布坐标」和「屏幕像素坐标」互转。
 *
 * 关键点：绘制与触摸**必须共用同一个 PageViewport**，否则会出现
 * 「页面跑到右下角」和「笔迹和笔尖错位」的问题。
 *
 * 约定：屏幕坐标 p 与逻辑坐标 l 的关系为
 *   p = l * scale + (offsetX, offsetY)
 * 也就是统一以页面左上角为原点（对应 DrawScope.scale 的 pivot = Offset.Zero）。
 */
data class PageViewport(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float,
    val canvasW: Float,
    val canvasH: Float
) {

    /** 屏幕像素 -> 逻辑画布坐标 */
    fun toLogical(x: Float, y: Float): Offset =
        Offset((x - offsetX) / scale, (y - offsetY) / scale)

    /** 逻辑画布坐标 -> 屏幕像素 */
    fun toScreen(x: Float, y: Float): Offset =
        Offset(x * scale + offsetX, y * scale + offsetY)

    val pageWidthPx: Float get() = canvasW * scale
    val pageHeightPx: Float get() = canvasH * scale

    companion object {
        val Empty = PageViewport(1f, 0f, 0f, 1f, 1f)

        /**
         * 计算视口。
         *
         * @param viewW    可视区宽度（像素）
         * @param viewH    可视区高度（像素）
         * @param canvasW  逻辑画布宽度
         * @param canvasH  逻辑画布高度
         * @param zoom     相对「适应屏幕」的缩放倍数
         * @param pan      相对居中位置的平移量（像素）
         */
        fun compute(
            viewW: Float,
            viewH: Float,
            canvasW: Float,
            canvasH: Float,
            zoom: Float = 1f,
            pan: Offset = Offset.Zero
        ): PageViewport {
            if (viewW <= 0f || viewH <= 0f || canvasW <= 0f || canvasH <= 0f) return Empty
            val base = min(viewW / canvasW, viewH / canvasH)
            val scale = base * zoom
            val offsetX = (viewW - canvasW * scale) / 2f + pan.x
            val offsetY = (viewH - canvasH * scale) / 2f + pan.y
            return PageViewport(scale, offsetX, offsetY, canvasW, canvasH)
        }
    }
}
