package com.finisdiscago.leximvp.feature.reading

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.finisdiscago.leximvp.core.ui.LexiDarkColors
import com.finisdiscago.leximvp.core.ui.LexiLightColors
import com.finisdiscago.leximvp.domain.model.ArticleWordStatus
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderContrastTest {
    @Test
    fun bodyPopupsAndEveryWordStatusRemainReadableInBothModes() {
        listOf(com.finisdiscago.leximvp.core.ui.LexiTealDark, com.finisdiscago.leximvp.core.ui.LexiTeal, com.finisdiscago.leximvp.core.ui.LexiAmber, com.finisdiscago.leximvp.core.ui.LexiCoral).forEach {
            assertReadable(com.finisdiscago.leximvp.core.ui.readableContentColor(it), it)
        }
        listOf(LexiLightColors, LexiDarkColors).forEach { colors ->
            assertReadable(colors.onPrimary, colors.primary)
            assertReadable(colors.primary, colors.background)
            assertReadable(colors.onBackground, colors.background)
            assertReadable(colors.onSurface, colors.surface)
            assertReadable(colors.onSurfaceVariant, colors.surfaceVariant)
            assertReadable(colors.onPrimaryContainer, colors.primaryContainer)
            ArticleWordStatus.entries.forEach { status ->
                val style = colors.readerTokenStyle(status)
                val background = if (style.background == Color.Transparent) colors.background else style.background
                assertReadable(style.color, background)
            }
        }
    }

    private fun assertReadable(foreground: Color, background: Color) {
        val a = foreground.luminance()
        val b = background.luminance()
        val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
        assertTrue("Text contrast must be at least 4.5:1, was $ratio", ratio >= 4.5f)
    }
}
