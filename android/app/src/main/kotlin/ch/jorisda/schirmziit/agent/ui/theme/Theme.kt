package ch.jorisda.schirmziit.agent.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * Material 3 roles, filled with the dashboard's palette so the phone and the
 * parent's browser look like one product. Deliberately NOT dynamic colour: a
 * child's wallpaper should not recolour a screen whose job is to be legible
 * about what is being collected.
 */
private val Paper = Color(0xFFF2F0EA)
private val Card = Color(0xFFFBFAF7)
private val Ink = Color(0xFF232622)
private val InkMuted = Color(0xFF5B5F59)
private val Hairline = Color(0xFFDFDCD2)
private val Accent = Color(0xFF00707E)
private val Warn = Color(0xFFC87C2C)
private val Urgent = Color(0xFFB4472C)

private val DarkPaper = Color(0xFF1C1A17)
private val DarkCard = Color(0xFF252320)
private val DarkInk = Color(0xFFF0EDE6)
private val DarkInkMuted = Color(0xFFA7A29A)
private val DarkHairline = Color(0xFF3A3733)
private val DarkAccent = Color(0xFF46B3BF)
private val DarkWarn = Color(0xFFD69A4A)
private val DarkUrgent = Color(0xFFDD7154)

private val LightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Card,
    secondary = InkMuted,
    background = Paper,
    onBackground = Ink,
    surface = Card,
    onSurface = Ink,
    surfaceVariant = Paper,
    onSurfaceVariant = InkMuted,
    outlineVariant = Hairline,
    error = Urgent,
    tertiary = Warn,
)

private val DarkColors = darkColorScheme(
    primary = DarkAccent,
    onPrimary = DarkPaper,
    secondary = DarkInkMuted,
    background = DarkPaper,
    onBackground = DarkInk,
    surface = DarkCard,
    onSurface = DarkInk,
    surfaceVariant = DarkPaper,
    onSurfaceVariant = DarkInkMuted,
    outlineVariant = DarkHairline,
    error = DarkUrgent,
    tertiary = DarkWarn,
)

/** Material's type scale, tightened: headlines carry the personality, body stays plain. */
private val SchirmziitTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    )
}

@Composable
fun SchirmziitTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val view = LocalContext.current as? Activity

    SideEffect {
        view?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = !dark
        }
    }

    MaterialTheme(colorScheme = colors, typography = SchirmziitTypography, content = content)
}
