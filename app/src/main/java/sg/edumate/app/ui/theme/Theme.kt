package sg.edumate.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Calm teal + warm accent. Contrast checked for WCAG AA on primary/onPrimary and surface/onSurface.
private val Light = lightColorScheme(
    primary = Color(0xFF1F6F78), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE9EA), onPrimaryContainer = Color(0xFF00363B),
    secondary = Color(0xFF52616B), secondaryContainer = Color(0xFFDDE5EB), onSecondaryContainer = Color(0xFF0F1D25),
    tertiary = Color(0xFF8A5A00), tertiaryContainer = Color(0xFFFFDDB0), onTertiaryContainer = Color(0xFF2C1900),
    background = Color(0xFFF7FAF9), surface = Color(0xFFF7FAF9), onSurface = Color(0xFF181C1C),
    surfaceVariant = Color(0xFFE2E8E8), onSurfaceVariant = Color(0xFF3F4949),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFF9DEDC),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8ED1D7), onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F56), onPrimaryContainer = Color(0xFFCDE9EA),
    secondary = Color(0xFFB9C9D3), secondaryContainer = Color(0xFF3A4952), onSecondaryContainer = Color(0xFFDDE5EB),
    tertiary = Color(0xFFFFB94D), tertiaryContainer = Color(0xFF684300), onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF101414), surface = Color(0xFF101414), onSurface = Color(0xFFE0E3E3),
    surfaceVariant = Color(0xFF3F4949), onSurfaceVariant = Color(0xFFBEC8C8),
)

/** Status colours for live monitoring. Paired with icons/labels so colour is never the only cue. */
object StatusColors {
    val notStarted = Color(0xFF8A9393)
    val working = Color(0xFF2E7D32)
    val stuck = Color(0xFFC62828)
    val done = Color(0xFF1565C0)
}

@Composable
fun EduMateTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
