package com.finisdiscago.leximvp.core.text

import com.finisdiscago.leximvp.domain.model.CardType
import org.json.JSONObject

data class ExerciseDefinition(val id: String, val type: CardType, val prompt: String, val answer: String,
    val options: List<String> = emptyList(), val explanation: String = "")

object ExerciseCapabilityResolver {
    fun exercises(word: String, raw: String?): List<ExerciseDefinition> {
        val root = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyList()
        val explicit = root.optJSONArray("exercises")
        val result = mutableListOf<ExerciseDefinition>()
        if (explicit != null) for (i in 0 until explicit.length()) {
            val value = explicit.optJSONObject(i) ?: continue
            val type = runCatching { CardType.valueOf(value.optString("type")) }.getOrNull() ?: continue
            val prompt = value.optString("prompt"); val answer = value.optString("answer")
            val choices = value.optJSONArray("options")
            val options = if (choices == null) emptyList() else (0 until choices.length()).map { choices.optString(it) }
            if (answer.isBlank() || prompt.isBlank()) continue
            if (type in setOf(CardType.AudioToChoice, CardType.SynonymDiscrimination) &&
                (options.distinct().size < 2 || answer !in options)) continue
            result += ExerciseDefinition(value.optString("id", "explicit:$i"), type, prompt, answer, options, value.optString("explanation"))
        }
        val rich = RichWordContentParser.parseOrNull(raw)
        val target = Regex("(?i)(?<![\\p{L}])" + Regex.escape(word) + "(?![\\p{L}])")
        rich?.senses?.forEachIndexed { senseIndex, sense ->
            sense.examples.forEachIndexed { exampleIndex, example ->
                if (target.containsMatchIn(example.en)) result += ExerciseDefinition("cloze:$senseIndex:$exampleIndex",
                    CardType.SentenceCloze, target.replace(example.en, "____"), word, explanation = example.zh)
            }
        }
        return result
    }

    fun types(word: String, meaning: String, raw: String?): List<CardType> =
        if (meaning.isBlank()) emptyList() else
            (listOf(CardType.ForeignToMeaning, CardType.MeaningToForeign, CardType.AudioToSpelling) +
                exercises(word, raw).map { it.type }).distinct()
}
