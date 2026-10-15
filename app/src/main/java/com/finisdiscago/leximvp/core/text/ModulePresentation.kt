package com.finisdiscago.leximvp.core.text

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Explicit presentation variants, following the recovered frontend's typed-state approach. */
sealed interface ModulePresentation {
    data class Text(val value: String) : ModulePresentation
    data class Items(val values: List<String>) : ModulePresentation
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ModulePresentation
    data class Examples(val values: List<Example>) : ModulePresentation
    data class Relations(val values: List<Relation>) : ModulePresentation
    data class Media(val uri: String, val caption: String) : ModulePresentation
    data class Fallback(val value: String) : ModulePresentation
    data class Example(val english: String, val translation: String)
    data class Relation(val target: String, val type: String, val note: String)
}

object ModulePresentationParser {
    fun parse(type: String, payload: String): ModulePresentation {
        val value = if (payload.trimStart().firstOrNull() in setOf('{', '[', '"')) {
            runCatching { JSONTokener(payload).nextValue() }.getOrDefault(payload)
        } else payload
        return when (type) {
            "text", "notes" -> ModulePresentation.Text(text(value))
            "list" -> ModulePresentation.Items(items(value).map(::text))
            "table" -> (value as? JSONObject).let { table ->
                ModulePresentation.Table(strings(table?.optJSONArray("headers")),
                    table?.optJSONArray("rows")?.let(::items).orEmpty().map { strings(it as? JSONArray) })
            }
            "examples" -> ModulePresentation.Examples(items(value).map { item ->
                val example = item as? JSONObject
                ModulePresentation.Example(example?.optString("en").orEmpty().ifBlank { text(item) },
                    example?.optString("zh").orEmpty())
            })
            "relations" -> ModulePresentation.Relations(items(value).map { item ->
                val relation = item as? JSONObject
                ModulePresentation.Relation(relation?.optString("target").orEmpty().ifBlank { text(item) },
                    relation?.optString("type").orEmpty(), relation?.optString("note").orEmpty())
            })
            "media" -> (value as? JSONObject).let {
                ModulePresentation.Media(it?.optString("uri").orEmpty(), it?.optString("caption").orEmpty())
            }
            else -> ModulePresentation.Fallback(readable(value))
        }
    }

    private fun items(value: Any?): List<Any?> =
        if (value is JSONArray) (0 until value.length()).map(value::opt) else listOf(value)

    private fun strings(value: JSONArray?): List<String> =
        if (value == null) emptyList() else (0 until value.length()).map { value.optString(it) }

    private fun text(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject -> value.optString("value").ifBlank { value.toString() }
        else -> value.toString()
    }

    private fun readable(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().joinToString("\n") { "$it：${readable(value.opt(it))}" }
        is JSONArray -> (0 until value.length()).joinToString("\n") { readable(value.opt(it)) }
        else -> text(value)
    }
}
