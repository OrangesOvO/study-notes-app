package com.example.studyink.model

/** 当前选中的书写工具（含图形与橡皮） */
enum class Tool(val label: String) {
    PEN("钢笔"),
    MARKER("马克笔"),
    HIGHLIGHTER("荧光笔"),
    LINE("直线"),
    RECT("矩形"),
    OVAL("圆形"),
    ERASER("橡皮");

    /** 落到数据库里的笔迹类型；橡皮不产生笔迹 */
    val penTypeOrNull: PenType?
        get() = when (this) {
            PEN -> PenType.PEN
            MARKER -> PenType.MARKER
            HIGHLIGHTER -> PenType.HIGHLIGHTER
            LINE -> PenType.LINE
            RECT -> PenType.RECT
            OVAL -> PenType.OVAL
            ERASER -> null
        }

    /** 切换工具时的默认笔宽（逻辑像素） */
    val defaultWidth: Float
        get() = when (this) {
            PEN -> 8f
            MARKER -> 20f
            HIGHLIGHTER -> 36f
            LINE, RECT, OVAL -> 6f
            ERASER -> 20f
        }
}
