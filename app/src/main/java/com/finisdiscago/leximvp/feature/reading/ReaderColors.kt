package com.finisdiscago.leximvp.feature.reading

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.finisdiscago.leximvp.domain.model.ArticleWordStatus

// Always pair a highlight with its foreground; body text alone cannot provide
// readable contrast on both the light and dark highlight surfaces.
internal fun ColorScheme.readerTokenStyle(status: ArticleWordStatus): SpanStyle = when (status) {
    ArticleWordStatus.Unlearned -> SpanStyle(color = onTertiaryContainer, background = tertiaryContainer)
    ArticleWordStatus.Learning -> SpanStyle(color = onSecondaryContainer, background = secondaryContainer)
    else -> SpanStyle(color = onBackground, background = Color.Transparent)
}
