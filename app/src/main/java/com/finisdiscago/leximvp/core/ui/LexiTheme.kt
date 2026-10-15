package com.finisdiscago.leximvp.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

val LexiTeal = Color(0xFF2FB49B)
val LexiTealDark = Color(0xFF087866)
val LexiAmber = Color(0xFFEE9B22)
val LexiCoral = Color(0xFFE2633E)
val LexiInk = Color(0xFF17211F)
val LexiMist = Color(0xFFF4F7F6)

internal val LexiLightColors = lightColorScheme(
    primary = LexiTealDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8F3EC),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Color(0xFF4A635E),
    secondaryContainer = Color(0xFFCDE8E1),
    tertiary = LexiAmber,
    tertiaryContainer = Color(0xFFFFD88A),
    onTertiaryContainer = Color(0xFF342300),
    error = Color(0xFFBA1A1A),
    background = LexiMist,
    onBackground = LexiInk,
    surface = Color(0xFFFFFFFF),
    onSurface = LexiInk,
    surfaceVariant = Color(0xFFEAF1EF),
    onSurfaceVariant = Color(0xFF414D49),
    outline = Color(0xFF7A8480),
    outlineVariant = Color(0xFFDDE2DF)
)

internal val LexiDarkColors = darkColorScheme(
    primary = Color(0xFF39C3AA),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF123F38),
    onPrimaryContainer = Color(0xFFD8F3EC),
    secondary = Color(0xFFB1CCC5),
    secondaryContainer = Color(0xFF334B47),
    onSecondaryContainer = Color(0xFFE0F1EC),
    tertiary = Color(0xFFFFB85C),
    tertiaryContainer = Color(0xFF604514),
    onTertiaryContainer = Color(0xFFFFE2AA),
    error = Color(0xFFFF8A6C),
    background = Color(0xFF101514),
    onBackground = Color(0xFFE2EBE7),
    surface = Color(0xFF19201F),
    onSurface = Color(0xFFE2EBE7),
    surfaceVariant = Color(0xFF222B29),
    onSurfaceVariant = Color(0xFFBECBC5),
    outline = Color(0xFF8B9491),
    outlineVariant = Color(0xFF343A3A)
)

private val LexiTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = (-1).sp),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp)
    )
}

private val LexiShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(30.dp)
)

@Composable
fun LexiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) LexiDarkColors else LexiLightColors,
        typography = LexiTypography,
        shapes = LexiShapes,
        content = content
    )
}

internal fun readableContentColor(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White
