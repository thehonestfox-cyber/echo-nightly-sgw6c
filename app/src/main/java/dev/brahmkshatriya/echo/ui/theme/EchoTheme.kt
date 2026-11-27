package dev.brahmkshatriya.echo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/** Echo brand colours (`app_color`, `app_color_highlight`, `app_color_shadow`). */
object EchoColors {
    val Primary = Color(0xFF22BBFF)
    val PrimaryVariant = Color(0xFF0A8FD6)
    val Highlight = Color(0xFFBFD5FF)
    val Surface = Color(0xFF1C1F21)
    val SurfaceVariant = Color(0xFF303133)
    val OnSurfaceVariant = Color(0xFFB8C4CC)
    val Error = Color(0xFFFF6E6E)
    val Dark = Color(0xFF2F3233)
}

private val WearColorPalette = Colors(
    primary = EchoColors.Primary,
    primaryVariant = EchoColors.PrimaryVariant,
    secondary = EchoColors.Highlight,
    secondaryVariant = EchoColors.Highlight,
    background = Color.Black,
    surface = EchoColors.Surface,
    error = EchoColors.Error,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = EchoColors.OnSurfaceVariant,
    onError = Color.Black,
)

@Composable
fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = WearColorPalette,
        content = content
    )
}
