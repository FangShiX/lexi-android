package com.finisdiscago.leximvp.domain.model

/** The payload is preserved verbatim, including extensions this app cannot render yet. */
data class ContentModule(
    val id: String,
    val type: String,
    val version: Int = 1,
    val title: String = type,
    val order: Int = 0,
    val hidden: Boolean = false,
    val collapsed: Boolean = false,
    val senseId: String? = null,
    val source: String = "",
    val status: String = "unverified",
    val payload: String = "{}",
    val warning: String? = null
)

data class LexicalEntry(
    val id: String,
    val language: String,
    val headword: String,
    val kind: String,
    val modules: List<ContentModule>
)

data class SourceRecord(val id: String, val name: String, val license: String = "")
data class EntryRelation(val target: String, val type: String, val senseId: String? = null)
data class SavedVocabularyView(val name: String, val query: String, val status: String = "", val order: String = "source")
