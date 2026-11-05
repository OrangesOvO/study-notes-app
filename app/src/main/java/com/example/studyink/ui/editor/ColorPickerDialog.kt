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

private val PresetColors = listOf(
    Color(0xFF1A1A1A), Color(0xFF616161), Color(0xFF9E9E9E),
    Color(0xFFD32F2F), Color(0xFFE64A19), Color(0xFFF9A825),
    Color(0xFF388E3C), Color(0xFF00897B), Color(0xFF1976D2),
    Color(0xFF3D5AFE), Color(0xFF7B1FA2), Color(0xFFC2185B)
)

@Composable
fun ColorPickerDialog(
    initial: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(initial.toArgb(), hsv)
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }

    val preview = Color.hsv(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择颜色") },
        text = {
            Column {
                // 预设色板
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(PresetColors) { c ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (preview == c) 3.dp else 1.dp,
                                    color = if (preview == c) Color(0xFF3D5AFE) else Color(0xFFCCCCCC),
                                    shape = CircleShape
                                )
                                .clickable {
                                    val arr = FloatArray(3)
                                    android.graphics.Color.colorToHSV(c.toArgb(), arr)
                                    hue = arr[0]
                                    saturation = arr[1]
                                    value = arr[2]
                                }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(preview)
                            .border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(16.dp))
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
        Text(label, modifier = Modifier.width(40.dp))
        Slider(
            value = v,
            onValueChange = onChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f)
        )
    }
}
