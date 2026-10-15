package com.finisdiscago.leximvp.core.text

import org.json.JSONArray
import org.json.JSONObject

data class ContentMergeResult(val json: String, val conflicts: List<String>)
data class ImportConflict(val index: Int, val fields: List<String>)

/** Existing values win. Conflicting incoming values remain available for manual reconciliation. */
object ContentMergeService {
    fun merge(existing: String?, incoming: String): ContentMergeResult {
        val conflicts = mutableListOf<String>()
        val target = existing?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { JSONObject(raw) }.getOrElse {
                conflicts += "existingContent.invalidJson"
                JSONObject().put("invalidExistingContent", raw)
            }
        } ?: JSONObject()
        val source = JSONObject(incoming)
        fun fill(old: JSONObject, fresh: JSONObject, path: String) {
            fresh.keys().forEach { key ->
                if (key == "importConflicts") return@forEach
                val next = fresh.get(key)
                val current = old.opt(key)
                val field = if (path.isEmpty()) key else "$path.$key"
                when {
                    current == null || current == JSONObject.NULL || current == "" ||
                        current is JSONArray && current.length() == 0 -> old.put(key, next)
                    current is JSONObject && next is JSONObject -> fill(current, next, field)
                    current.toString() != next.toString() -> conflicts.add(field)
                }
            }
        }
        fill(target, source, "")
        if (conflicts.isNotEmpty()) {
            val reports = target.optJSONArray("importConflicts") ?: JSONArray()
            // Idempotent import: do not keep adding an identical conflict snapshot.
            val report = JSONObject().put("fields", JSONArray(conflicts)).put("incoming", source)
            if ((0 until reports.length()).none { reports.opt(it).toString() == report.toString() }) reports.put(report)
            target.put("importConflicts", reports)
        }
        return ContentMergeResult(target.toString(), conflicts)
    }

    fun conflicts(raw: String?): List<ImportConflict> {
        val reports = raw?.let { runCatching { JSONObject(it).optJSONArray("importConflicts") }.getOrNull() } ?: return emptyList()
        return (0 until reports.length()).mapNotNull { index ->
            val report = reports.optJSONObject(index) ?: return@mapNotNull null
            val fields = report.optJSONArray("fields") ?: JSONArray()
            ImportConflict(index, (0 until fields.length()).map { fields.optString(it) })
        }
    }

    fun resolve(raw: String, conflictIndex: Int, acceptIncoming: Boolean): String {
        val root = JSONObject(raw)
        val reports = root.optJSONArray("importConflicts") ?: return raw
        val report = reports.optJSONObject(conflictIndex) ?: return raw
        if (acceptIncoming) {
            val incoming = report.optJSONObject("incoming") ?: JSONObject()
            val fields = report.optJSONArray("fields") ?: JSONArray()
            for (index in 0 until fields.length()) {
                val path = fields.optString(index).split('.').filter(String::isNotBlank)
                valueAt(incoming, path)?.let { putAt(root, path, it) }
            }
        }
        reports.remove(conflictIndex)
        if (reports.length() == 0) root.remove("importConflicts") else root.put("importConflicts", reports)
        return root.toString()
    }

    private fun valueAt(root: JSONObject, path: List<String>): Any? {
        var current: Any = root
        path.forEach { key -> current = (current as? JSONObject)?.opt(key) ?: return null }
        return current
    }

    private fun putAt(root: JSONObject, path: List<String>, value: Any) {
        if (path.isEmpty()) return
        var current = root
        path.dropLast(1).forEach { key ->
            current = current.optJSONObject(key) ?: JSONObject().also { current.put(key, it) }
        }
        current.put(path.last(), value)
    }
}
