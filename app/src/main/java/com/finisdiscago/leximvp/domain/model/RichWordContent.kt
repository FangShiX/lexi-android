package com.finisdiscago.leximvp.domain.model

data class RichWordContent(
    val word: String,
    val pronunciation: RichPronunciation,
    val basicMeanings: List<RichBasicMeaning>,
    val coreMeaning: RichCoreMeaning,
    val etymology: RichEtymology,
    val senses: List<RichWordSense>,
    val wordFormation: RichWordFormation,
    val confusables: List<RichConfusable>,
    val semanticNetwork: RichSemanticNetwork,
    val chunks: List<RichChunk>
) {
    fun selectSense(seed: Long): RichWordSense? =
        senses.takeIf(List<RichWordSense>::isNotEmpty)
            ?.get(Math.floorMod(seed, senses.size.toLong()).toInt())
}

data class RichPronunciation(
    val ukIpa: String,
    val usIpa: String,
    val syllables: String,
    val stressNote: String
)

data class RichBasicMeaning(
    val partOfSpeech: String,
    val meaningsZh: List<String>
)

data class RichCoreMeaning(
    val nativeConcept: String,
    val senseConnections: String
)

data class RichEtymology(
    val origin: String,
    val development: String,
    val confidenceNote: String
)

data class RichExample(val en: String, val zh: String)

data class RichWordSense(
    val partOfSpeech: String,
    val meaningZh: String,
    val definitionEn: String,
    val contexts: List<String>,
    val frequency: String,
    val register: List<String>,
    val connotation: String,
    val semanticBoundary: String,
    val collocations: List<String>,
    val patterns: List<String>,
    val nativeAssociations: List<String>,
    val examples: List<RichExample>
)

data class RichMorpheme(val form: String, val meaning: String, val note: String)
data class RichWordForm(val word: String, val partOfSpeech: String, val meaningZh: String)
data class RichFamilyMember(val word: String, val meaningZh: String)

data class RichWordFormation(
    val morphemes: List<RichMorpheme>,
    val forms: List<RichWordForm>,
    val family: List<RichFamilyMember>
)

data class RichConfusable(
    val word: String,
    val relation: String,
    val distinction: String,
    val choiceGuide: String,
    val comparativeExamples: List<RichExample>
)

data class RichSemanticRelation(val word: String, val note: String)
data class RichRelatedWord(val word: String, val relation: String)

data class RichSemanticNetwork(
    val antonyms: List<RichSemanticRelation>,
    val hypernyms: List<RichSemanticRelation>,
    val hyponyms: List<RichSemanticRelation>,
    val related: List<RichRelatedWord>
)

data class RichChunk(
    val expression: String,
    val meaningZh: String,
    val register: String,
    val example: RichExample
)
