package com.example.studyink.util

import com.example.studyink.model.InkPoint

/**
 * 笔迹点序列化。格式（无外部依赖）：
 * "x,y,t,p;x,y,t,p;..."，点之间用分号分隔，字段用逗号分隔。
 */
object StrokeSerializer {

    fun serialize(points: List<InkPoint>): String {
        if (points.isEmpty()) return ""
        val sb = StringBuilder(points.size * 16)
        for (p in points) {
            if (sb.isNotEmpty()) sb.append(';')
            sb.append(p.x).append(',')
                .append(p.y).append(',')
                .append(p.t).append(',')
                .append(p.pressure)
        }
        return sb.toString()
    }

    fun deserialize(s: String): List<InkPoint> {
        if (s.isEmpty()) return emptyList()
        val result = ArrayList<InkPoint>()
        for (part in s.split(';')) {
            val a = part.split(',')
            if (a.size < 4) continue
            result.add(
                InkPoint(
                    x = a[0].toFloat(),
                    y = a[1].toFloat(),
                    t = a[2].toLong(),
                    pressure = a[3].toFloat()
                )
            )
        }
        return result
    }
}
