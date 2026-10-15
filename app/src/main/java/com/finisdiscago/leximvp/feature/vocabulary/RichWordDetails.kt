package com.finisdiscago.leximvp.feature.vocabulary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finisdiscago.leximvp.core.ui.LexiSectionCard
import com.finisdiscago.leximvp.core.ui.LexiStatusPill
import com.finisdiscago.leximvp.domain.model.*

@Composable
fun RichWordContentBody(content: RichWordContent, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (content.basicMeanings.isNotEmpty()) {
            LexiSectionCard {
                SectionTitle("释义")
                content.basicMeanings.forEach { meaning ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LexiStatusPill(meaning.partOfSpeech.ifBlank { "释义" })
                        Text(meaning.meaningsZh.joinToString("；"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        content.senses.forEachIndexed { index, sense ->
            ExpandableSection("义项 ${index + 1} · ${sense.partOfSpeech.ifBlank { "未标词性" }}", expandedInitially = index == 0) {
                RichSenseContent(sense)
            }
        }
        if (content.coreMeaning.nativeConcept.isNotBlank() || content.coreMeaning.senseConnections.isNotBlank()) {
            ExpandableSection("核心概念") {
                DetailLine("概念", content.coreMeaning.nativeConcept)
                DetailLine("义项联系", content.coreMeaning.senseConnections)
            }
        }
        if (content.chunks.isNotEmpty()) ExpandableSection("搭配与句式") {
            content.chunks.forEach { chunk ->
                Text(chunk.expression, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                DetailLine("含义", chunk.meaningZh)
                DetailLine("语域", chunk.register)
                Example(chunk.example)
            }
        }
        if (content.wordFormation.morphemes.isNotEmpty() || content.wordFormation.forms.isNotEmpty() || content.wordFormation.family.isNotEmpty()) {
            ExpandableSection("构词与词形") {
                content.wordFormation.morphemes.forEach { DetailLine(it.form, listOf(it.meaning, it.note).filter(String::isNotBlank).joinToString(" · ")) }
                content.wordFormation.forms.forEach { DetailLine(it.word, "${it.partOfSpeech} ${it.meaningZh}".trim()) }
                content.wordFormation.family.forEach { DetailLine(it.word, it.meaningZh) }
            }
        }
        if (content.confusables.isNotEmpty()) ExpandableSection("辨析") {
            content.confusables.forEach { item ->
                Text("${item.word} · ${item.relation}", fontWeight = FontWeight.SemiBold)
                Text(item.distinction)
                DetailLine("选择依据", item.choiceGuide)
                item.comparativeExamples.forEach { Example(it) }
            }
        }
        val network = content.semanticNetwork
        if (network.antonyms.isNotEmpty() || network.hypernyms.isNotEmpty() || network.hyponyms.isNotEmpty() || network.related.isNotEmpty()) {
            ExpandableSection("语义关系") {
                RelationLine("反义", network.antonyms.map { "${it.word}${it.note.takeIf(String::isNotBlank)?.let { note -> "（$note）" }.orEmpty()}" })
                RelationLine("上位", network.hypernyms.map { it.word })
                RelationLine("下位", network.hyponyms.map { it.word })
                RelationLine("相关", network.related.map { "${it.word} · ${it.relation}" })
            }
        }
        if (content.etymology.origin.isNotBlank() || content.etymology.development.isNotBlank()) {
            ExpandableSection("词源") {
                DetailLine("来源", content.etymology.origin)
                DetailLine("演变", content.etymology.development)
                DetailLine("证据", content.etymology.confidenceNote)
            }
        }
    }
}

@Composable
fun RichWordModule(content: RichWordContent, type: String, collapsed: Boolean) {
    when (type) {
        "pronunciation" -> ExpandableSection("发音", !collapsed) {
            DetailLine("英音", content.pronunciation.ukIpa)
            DetailLine("美音", content.pronunciation.usIpa)
            DetailLine("音节", content.pronunciation.syllables)
            DetailLine("重音", content.pronunciation.stressNote)
        }
        "senses" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (content.basicMeanings.isNotEmpty()) LexiSectionCard {
                SectionTitle("释义")
                content.basicMeanings.forEach { meaning ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        LexiStatusPill(meaning.partOfSpeech.ifBlank { "释义" })
                        Text(meaning.meaningsZh.joinToString("；"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            content.senses.forEachIndexed { index, sense ->
                ExpandableSection("义项 ${index + 1} · ${sense.partOfSpeech.ifBlank { "未标词性" }}", !collapsed && index == 0) { RichSenseContent(sense) }
            }
        }
        "coreMeaning" -> ExpandableSection("核心概念", !collapsed) {
            DetailLine("概念", content.coreMeaning.nativeConcept)
            DetailLine("义项联系", content.coreMeaning.senseConnections)
        }
        "etymology" -> ExpandableSection("词源", !collapsed) {
            DetailLine("来源", content.etymology.origin)
            DetailLine("演变", content.etymology.development)
            DetailLine("证据", content.etymology.confidenceNote)
        }
        "wordFormation" -> ExpandableSection("构词与词形", !collapsed) {
            content.wordFormation.morphemes.forEach { DetailLine(it.form, listOf(it.meaning, it.note).filter(String::isNotBlank).joinToString(" · ")) }
            content.wordFormation.forms.forEach { DetailLine(it.word, "${it.partOfSpeech} ${it.meaningZh}".trim()) }
            content.wordFormation.family.forEach { DetailLine(it.word, it.meaningZh) }
        }
        "confusables" -> ExpandableSection("辨析", !collapsed) {
            content.confusables.forEach { item ->
                Text("${item.word} · ${item.relation}", fontWeight = FontWeight.SemiBold)
                Text(item.distinction)
                DetailLine("选择依据", item.choiceGuide)
                item.comparativeExamples.forEach { Example(it) }
            }
        }
        "semanticNetwork" -> ExpandableSection("语义关系", !collapsed) {
            val network = content.semanticNetwork
            RelationLine("反义", network.antonyms.map { it.word })
            RelationLine("上位", network.hypernyms.map { it.word })
            RelationLine("下位", network.hyponyms.map { it.word })
            RelationLine("相关", network.related.map { "${it.word} · ${it.relation}" })
        }
        "chunks" -> ExpandableSection("搭配与句式", !collapsed) {
            content.chunks.forEach { chunk ->
                Text(chunk.expression, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                DetailLine("含义", chunk.meaningZh)
                DetailLine("语域", chunk.register)
                Example(chunk.example)
            }
        }
    }
}

@Composable
fun RichSenseContent(sense: RichWordSense) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(sense.meaningZh, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (sense.definitionEn.isNotBlank()) Text(sense.definitionEn, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            sense.partOfSpeech.takeIf(String::isNotBlank)?.let { LexiStatusPill(it) }
            sense.frequency.takeIf(String::isNotBlank)?.let { LexiStatusPill(frequencyLabel(it)) }
            sense.register.take(2).forEach { LexiStatusPill(it) }
        }
        DetailLine("语境", sense.contexts.joinToString("、"))
        DetailLine("语义边界", sense.semanticBoundary)
        DetailLine("感情色彩", sense.connotation)
        RelationLine("搭配", sense.collocations)
        RelationLine("句型", sense.patterns)
        if (sense.examples.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("例句", fontWeight = FontWeight.SemiBold)
            sense.examples.forEach { Example(it) }
        }
    }
}

@Composable
private fun ExpandableSection(title: String, expandedInitially: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(expandedInitially) }
    LexiSectionCard {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, horizontalArrangement = Arrangement.SpaceBetween) {
            SectionTitle(title)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (expanded) "折叠" else "展开")
        }
        AnimatedVisibility(expanded) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() } }
    }
}

@Composable
private fun SectionTitle(title: String) = Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

@Composable
private fun DetailLine(label: String, value: String) {
    if (value.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 52.dp))
        Text(value, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun RelationLine(label: String, values: List<String>) {
    if (values.isNotEmpty()) DetailLine(label, values.joinToString("；"))
}

@Composable
private fun Example(example: RichExample) {
    Column(Modifier.padding(vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(example.en, style = MaterialTheme.typography.bodyLarge)
        if (example.zh.isNotBlank()) Text(example.zh, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun frequencyLabel(value: String): String = when (value) {
    "very_common" -> "非常常见"
    "common" -> "常见"
    "less_common" -> "较少见"
    "rare" -> "罕见"
    else -> value
}

@Composable
fun RichWordDetailDialog(content: RichWordContent, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(content.word) },
        text = { Box(Modifier.heightIn(max = 560.dp)) { androidx.compose.foundation.lazy.LazyColumn { item { RichWordContentBody(content) } } } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
