package com.finisdiscago.leximvp.core.text

import org.json.JSONArray
import org.json.JSONObject

/** Converts the compact editor syntax into stable typed module payloads. */
object ModulePayloadCodec {
    fun encode(type: String, input: String): Any = when (type) {
        "text" -> input.trim()
        "list" -> JSONArray(lines(input))
        "table" -> {
            val rows = lines(input).map { line -> line.split('|').map(String::trim) }
            JSONObject()
                .put("headers", JSONArray(rows.firstOrNull().orEmpty()))
                .put("rows", JSONArray(rows.drop(1).map { JSONArray(it) }))
        }
        "examples" -> JSONArray(lines(input).mapIndexed { index, line ->
            val pair = line.split("||", limit = 2)
            JSONObject().put("id", "example:$index").put("en", pair[0].trim())
                .put("zh", pair.getOrElse(1) { "" }.trim())
        })
        "relations" -> JSONArray(lines(input).mapIndexed { index, line ->
            val cells = line.split('|', limit = 3)
            JSONObject().put("id", "relation:$index").put("target", cells[0].trim())
                .put("type", cells.getOrElse(1) { "related" }.trim())
                .put("note", cells.getOrElse(2) { "" }.trim())
        })
        "media" -> {
            val values = input.lines()
            JSONObject().put("uri", values.firstOrNull().orEmpty().trim())
                .put("caption", values.drop(1).joinToString("\n").trim())
        }
        else -> runCatching { org.json.JSONTokener(input).nextValue() }.getOrDefault(input)
    }

    fun editorHint(type: String): String = when (type) {
        "list" -> "每行一项"
        "table" -> "第一行表头，单元格用 | 分隔"
        "examples" -> "每行：英文 || 中文"
        "relations" -> "每行：目标词 | 关系类型 | 说明"
        "media" -> "第一行填写本地文件或 content URI，后续行填写说明"
        else -> "输入内容"
    }

    fun decodeForEditor(type: String, payload: String): String {
        val value = runCatching { org.json.JSONTokener(payload).nextValue() }.getOrDefault(payload)
        return when (type) {
            "text" -> value.toString()
            "list" -> (value as? JSONArray).lines { optString(it) }
            "table" -> (value as? JSONObject)?.let { table ->
                val rows = mutableListOf<String>()
                table.optJSONArray("headers")?.let { rows += (0 until it.length()).joinToString(" | ") { index -> it.optString(index) } }
                table.optJSONArray("rows")?.let { array ->
                    for (index in 0 until array.length()) array.optJSONArray(index)?.let { row -> rows += (0 until row.length()).joinToString(" | ") { cell -> row.optString(cell) } }
                }
                rows.joinToString("\n")
            }.orEmpty()
            "examples" -> (value as? JSONArray).lines { index ->
                optJSONObject(index)?.let { "${it.optString("en")} || ${it.optString("zh")}" }.orEmpty()
            }
            "relations" -> (value as? JSONArray).lines { index ->
                optJSONObject(index)?.let { "${it.optString("target")} | ${it.optString("type")} | ${it.optString("note")}" }.orEmpty()
            }
            "media" -> (value as? JSONObject)?.let { listOf(it.optString("uri"), it.optString("caption")).filter(String::isNotBlank).joinToString("\n") }.orEmpty()
            else -> payload
        }
    }

    private fun lines(input: String): List<String> =
        input.lines().map(String::trim).filter(String::isNotEmpty)

    private fun JSONArray?.lines(value: JSONArray.(Int) -> String): String =
        if (this == null) "" else (0 until length()).joinToString("\n") { value(it) }
}
