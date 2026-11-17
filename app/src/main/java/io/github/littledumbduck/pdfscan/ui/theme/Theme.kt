package io.github.littledumbduck.pdfscan.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Brand = Color(0xFF1E5EFF)

private val LightColors = lightColorScheme(
    primary = Brand,
    secondary = Color(0xFF4D5C92),
    tertiary = Color(0xFFB8860B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    secondary = Color(0xFFB8C4FF),
    tertiary = Color(0xFFFFC83D),
)

@Composable
fun PdfScanTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
