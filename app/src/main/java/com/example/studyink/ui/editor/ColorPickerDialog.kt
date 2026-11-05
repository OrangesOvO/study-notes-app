package com.example.studyink.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.example.studyink.util.intToColor

/** 42 色预设色板（7 组 × 6 色） */
private val PresetColors: List<Color> = listOf(
    // 黑灰
    Color(0xFF000000), Color(0xFF1A1A1A), Color(0xFF424242),
    Color(0xFF616161), Color(0xFF9E9E9E), Color(0xFFCFD8DC),
    // 红
    Color(0xFFB71C1C), Color(0xFFD32F2F), Color(0xFFF44336),
    Color(0xFFE64A19), Color(0xFFF4511E), Color(0xFFFF7043),
    // 橙黄
    Color(0xFFFF6F00), Color(0xFFF9A825), Color(0xFFFFC107),
    Color(0xFFFFEB3B), Color(0xFFFFF176), Color(0xFFC0CA33),
    // 绿
    Color(0xFF1B5E20), Color(0xFF2E7D32), Color(0xFF43A047),
    Color(0xFF00897B), Color(0xFF009688), Color(0xFF26A69A),
    // 蓝
    Color(0xFF0D47A1), Color(0xFF1565C0), Color(0xFF1976D2),
    Color(0xFF3D5AFE), Color(0xFF0288D1), Color(0xFF00ACC1),
    // 紫
    Color(0xFF4A148C), Color(0xFF6A1B9A), Color(0xFF7B1FA2),
    Color(0xFF9C27B0), Color(0xFFAB47BC), Color(0xFFCE93D8),
    // 粉棕
    Color(0xFFC2185B), Color(0xFFE91E63), Color(0xFFF06292),
    Color(0xFF5D4037), Color(0xFF795548), Color(0xFFA1887F)
)

@Composable
fun ColorPickerDialog(
    initial: Color,
    recentColors: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(initial.toArgb(), hsv)
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }

    val preview = Color.hsv(hue, saturation, value)

    val select: (Color) -> Unit = { c ->
        val arr = FloatArray(3)
        android.graphics.Color.colorToHSV(c.toArgb(), arr)
        hue = arr[0]
        saturation = arr[1]
        value = arr[2]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column {
                if (recentColors.isNotEmpty()) {
                    Text("最近使用", modifier = Modifier.padding(bottom = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        recentColors.take(8).forEach { argb ->
                            val c = intToColor(argb)
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(1.dp, Color(0xFFCCCCCC), CircleShape)
                                    .clickable { select(c) }
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PresetColors) { c ->
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (preview == c) 3.dp else 1.dp,
                                    color = if (preview == c) Color(0xFF3D5AFE) else Color(0xFFCCCCCC),
                                    shape = CircleShape
                                )
                                .clickable { select(c) }
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(preview)
                            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        SliderRow("色相", hue, 0f, 360f) { hue = it }
                        SliderRow("饱和度", saturation, 0f, 1f) { saturation = it }
                        SliderRow("明度", value, 0f, 1f) { value = it }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(preview) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun SliderRow(label: String, v: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(44.dp))
        Slider(
            value = v,
            onValueChange = onChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f)
        )
    }
}
