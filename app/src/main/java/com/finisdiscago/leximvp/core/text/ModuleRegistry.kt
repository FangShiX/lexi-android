package com.finisdiscago.leximvp.core.text

import com.finisdiscago.leximvp.domain.model.ContentModule
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ModuleRegistry {
    val titles = linkedMapOf(
        "pronunciation" to "发音", "senses" to "义项", "examples" to "例句",
        "coreMeaning" to "核心概念", "etymology" to "词源", "wordFormation" to "构词与词形",
        "confusables" to "辨析", "semanticNetwork" to "语义关系", "chunks" to "搭配与句式",
        "notes" to "用法与个人笔记"
    )
    val customTypes = setOf("text", "list", "table", "examples", "relations", "media")

    fun read(raw: String?, defaultLayoutRaw: String? = null): List<ContentModule> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrElse {
            return listOf(ContentModule("invalid", "unknown", title = "内容格式错误", payload = raw, warning = "原始内容已保留，请修订 JSON"))
        }
        val modules = mutableListOf<ContentModule>()
        titles.entries.forEachIndexed { index, (type, title) ->
            if (root.has(type) && !root.isNull(type)) {
                val value = root.get(type)
                val valid = value is JSONObject || value is JSONArray || value is String
                modules += ContentModule("legacy:$type", type, title = title, order = index,
                    payload = JSONObject().put("value", value).toString(),
                    warning = if (valid) null else "模块结构无效，原始值已保留")
            }
        }
        val array = root.optJSONArray("modules") ?: JSONArray()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item == null) {
                modules += ContentModule("invalid:$index", "unknown", order = 100 + index,
                    payload = array.opt(index).toString(), warning = "模块必须是对象")
                continue
            }
            val type = item.optString("type", "unknown")
            modules += ContentModule(
                id = item.optString("id", "module:$index"), type = type,
                version = item.optInt("version", 1), title = item.optString("title", titles[type] ?: type),
                order = item.optInt("order", 100 + index), hidden = item.optBoolean("hidden"),
                collapsed = item.optBoolean("collapsed"), senseId = item.optString("senseId").takeIf { it.isNotBlank() },
                source = item.optString("source"), status = item.optString("status", "unverified"),
                payload = item.opt("content")?.toString() ?: "{}",
                warning = if (item.optInt("version", 1) != 1 || type !in titles && type !in customTypes)
                    "暂不支持此模块类型或版本，原始数据已保留" else null
            )
        }
        val defaults = defaultLayoutRaw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        val layout = root.optJSONObject("layout") ?: JSONObject()
        return modules.map { module ->
            val defaultSettings = defaults.optJSONObject(module.type) ?: JSONObject()
            val settings = layout.optJSONObject(module.id) ?: JSONObject()
            module.copy(
                order = settings.optInt("order", defaultSettings.optInt("order", module.order)),
                hidden = settings.optBoolean("hidden", defaultSettings.optBoolean("hidden", module.hidden)),
                collapsed = settings.optBoolean("collapsed", defaultSettings.optBoolean("collapsed", module.collapsed))
            )
        }.sortedBy { it.order }
    }

    fun add(raw: String?, type: String, title: String, content: String): String {
        require(type in customTypes || type in titles) { "未知模块类型" }
        val root = if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
        val array = root.optJSONArray("modules") ?: JSONArray()
        array.put(JSONObject().put("id", UUID.randomUUID().toString()).put("type", type)
            .put("version", 1).put("title", title).put("order", 100 + array.length())
            .put("status", "user").put("content", ModulePayloadCodec.encode(type, content)))
        return root.put("modules", array).toString()
    }

    fun layout(raw: String, id: String, order: Int, hidden: Boolean, collapsed: Boolean): String {
        val root = JSONObject(raw)
        val layout = root.optJSONObject("layout") ?: JSONObject()
        layout.put(id, JSONObject().put("order", order).put("hidden", hidden).put("collapsed", collapsed))
        return root.put("layout", layout).toString()
    }

    fun update(raw: String, id: String, title: String, content: String): String {
        val root = JSONObject(raw)
        val modules = root.optJSONArray("modules") ?: throw IllegalArgumentException("没有可编辑模块")
        for (index in 0 until modules.length()) {
            val module = modules.optJSONObject(index) ?: continue
            if (module.optString("id") == id) {
                val type = module.optString("type", "text")
                module.put("title", title).put("content", ModulePayloadCodec.encode(type, content)).put("status", "user")
                return root.toString()
            }
        }
        throw IllegalArgumentException("模块不存在")
    }

    fun remove(raw: String, id: String): String {
        val root = JSONObject(raw)
        val modules = root.optJSONArray("modules") ?: return raw
        for (index in modules.length() - 1 downTo 0) {
            if (modules.optJSONObject(index)?.optString("id") == id) modules.remove(index)
        }
        root.optJSONObject("layout")?.remove(id)
        return root.toString()
    }

    fun defaultLayout(modules: List<ContentModule>): String = JSONObject().apply {
        modules.forEach { module ->
            put(module.type, JSONObject().put("order", module.order)
                .put("hidden", module.hidden).put("collapsed", module.collapsed))
        }
    }.toString()
}
