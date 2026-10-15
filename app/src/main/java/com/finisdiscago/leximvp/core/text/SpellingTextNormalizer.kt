package com.finisdiscago.leximvp.core.text

import java.text.Normalizer
import java.util.Locale

/** Unicode normalization shared by spelling answer checks. */
object SpellingTextNormalizer {
    fun normalize(value: String): String = Normalizer.normalize(
        value.replace("ё", "e"), Normalizer.Form.NFD
    ).replace(Regex("[^\\p{ASCII}]"), "").lowercase(Locale.ROOT)
}
