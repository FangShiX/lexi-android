package com.finisdiscago.leximvp.core.text

import com.finisdiscago.leximvp.domain.model.RichBasicMeaning
import com.finisdiscago.leximvp.domain.model.RichChunk
import com.finisdiscago.leximvp.domain.model.RichConfusable
import com.finisdiscago.leximvp.domain.model.RichCoreMeaning
import com.finisdiscago.leximvp.domain.model.RichEtymology
import com.finisdiscago.leximvp.domain.model.RichExample
import com.finisdiscago.leximvp.domain.model.RichFamilyMember
import com.finisdiscago.leximvp.domain.model.RichMorpheme
import com.finisdiscago.leximvp.domain.model.RichPronunciation
import com.finisdiscago.leximvp.domain.model.RichRelatedWord
import com.finisdiscago.leximvp.domain.model.RichSemanticNetwork
import com.finisdiscago.leximvp.domain.model.RichSemanticRelation
import com.finisdiscago.leximvp.domain.model.RichWordContent
import com.finisdiscago.leximvp.domain.model.RichWordForm
import com.finisdiscago.leximvp.domain.model.RichWordFormation
import com.finisdiscago.leximvp.domain.model.RichWordSense
import org.json.JSONArray
import org.json.JSONObject

object RichWordContentParser {
    fun parseOrNull(json: String?): RichWordContent? =
        json?.let { runCatching { parse(it) }.getOrNull() }

    fun parse(json: String): RichWordContent {
        val root = JSONObject(json)
        val pronunciation = root.objectOrEmpty("pronunciation")
        val coreMeaning = root.objectOrEmpty("coreMeaning")
        val etymology = root.objectOrEmpty("etymology")
        val formation = root.objectOrEmpty("wordFormation")
        val network = root.objectOrEmpty("semanticNetwork")
        return RichWordContent(
            word = root.text("word"),
            pronunciation = RichPronunciation(
                ukIpa = pronunciation.text("ukIpa"),
                usIpa = pronunciation.text("usIpa"),
                syllables = pronunciation.text("syllables"),
                stressNote = pronunciation.text("stressNote")
            ),
            basicMeanings = root.array("basicMeanings").objects {
                RichBasicMeaning(it.text("partOfSpeech"), it.strings("meaningsZh"))
            },
            coreMeaning = RichCoreMeaning(
                coreMeaning.text("nativeConcept"),
                coreMeaning.text("senseConnections")
            ),
            etymology = RichEtymology(
                etymology.text("origin"),
                etymology.text("development"),
                etymology.text("confidenceNote")
            ),
            senses = root.array("senses").objects {
                RichWordSense(
                    partOfSpeech = it.text("partOfSpeech"),
                    meaningZh = it.text("meaningZh"),
                    definitionEn = it.text("definitionEn"),
                    contexts = it.strings("contexts"),
                    frequency = it.text("frequency"),
                    register = it.strings("register"),
                    connotation = it.text("connotation"),
                    semanticBoundary = it.text("semanticBoundary"),
                    collocations = it.strings("collocations"),
                    patterns = it.strings("patterns"),
                    nativeAssociations = it.strings("nativeAssociations"),
                    examples = it.array("examples").objects(::example)
                )
            },
            wordFormation = RichWordFormation(
                morphemes = formation.array("morphemes").objects {
                    RichMorpheme(it.text("form"), it.text("meaning"), it.text("note"))
                },
                forms = formation.array("forms").objects {
                    RichWordForm(it.text("word"), it.text("partOfSpeech"), it.text("meaningZh"))
                },
                family = formation.array("family").objects {
                    RichFamilyMember(it.text("word"), it.text("meaningZh"))
                }
            ),
            confusables = root.array("confusables").objects {
                RichConfusable(
                    word = it.text("word"),
                    relation = it.text("relation"),
                    distinction = it.text("distinction"),
                    choiceGuide = it.text("choiceGuide"),
                    comparativeExamples = it.array("comparativeExamples").objects(::example)
                )
            },
            semanticNetwork = RichSemanticNetwork(
                antonyms = network.relations("antonyms"),
                hypernyms = network.relations("hypernyms"),
                hyponyms = network.relations("hyponyms"),
                related = network.array("related").objects {
                    RichRelatedWord(it.text("word"), it.text("relation"))
                }
            ),
            chunks = root.array("chunks").objects {
                RichChunk(
                    expression = it.text("expression"),
                    meaningZh = it.text("meaningZh"),
                    register = it.text("register"),
                    example = example(it.objectOrEmpty("example"))
                )
            }
        )
    }

    private fun JSONObject.objectOrEmpty(name: String): JSONObject = optJSONObject(name) ?: JSONObject()

    private fun example(value: JSONObject) = RichExample(value.text("en"), value.text("zh"))

    private fun JSONObject.relations(name: String) = array(name).objects {
        RichSemanticRelation(it.text("word"), it.text("note"))
    }

    private fun JSONObject.text(name: String): String = optString(name, "")
    private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
    private fun JSONObject.strings(name: String): List<String> =
        array(name).let { array -> List(array.length()) { array.optString(it) } }

    private fun <T> JSONArray.objects(transform: (JSONObject) -> T): List<T> =
        (0 until length()).mapNotNull { index -> optJSONObject(index)?.let { runCatching { transform(it) }.getOrNull() } }
}
