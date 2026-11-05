package com.example.studyink.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = InkBlack,
    background = SurfaceGray,
    surface = CanvasWhite,
    surfaceVariant = SurfaceGray,
    outline = BorderGray
)

@Composable
fun StudyInkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        content = content
    )
}
