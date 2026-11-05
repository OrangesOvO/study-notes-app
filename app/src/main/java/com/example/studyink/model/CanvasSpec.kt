package com.example.studyink.model

/** 画布逻辑尺寸规格。所有笔迹坐标都落在该逻辑画布坐标系内。 */
object CanvasSpec {
    /** 空白笔记：A4 比例 @150dpi（宽 × 高，逻辑像素） */
    const val A4_WIDTH = 1240
    const val A4_HEIGHT = 1754

    /** PDF 页渲染成位图的缩放倍数（相对 PDF 点单位） */
    const val PDF_RENDER_SCALE = 2
}
