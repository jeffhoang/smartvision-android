package app.streammog.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AvaLensTeal,
    onPrimary = Color(0xFF003833),
    primaryContainer = AvaLensTealDim,
    onPrimaryContainer = Color(0xFFCCF7F0),
    secondary = Color(0xFF8FA5A9),
    onSecondary = Color(0xFF1A2C2E),
    background = AvaLensBackground,
    onBackground = AvaLensOnSurface,
    surface = AvaLensSurface,
    onSurface = AvaLensOnSurface,
    surfaceVariant = AvaLensSurfaceVariant,
    onSurfaceVariant = AvaLensOnSurfaceVariant,
    error = AvaLensError,
    onError = Color(0xFF690005),
    outline = Color(0xFF3A4A4E),
)

@Composable
fun AvaLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
