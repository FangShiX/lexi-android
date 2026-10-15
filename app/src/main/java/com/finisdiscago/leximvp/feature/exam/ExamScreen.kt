package com.finisdiscago.leximvp.feature.exam

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.finisdiscago.leximvp.LexiApplication
import com.finisdiscago.leximvp.core.database.entity.WorkspaceRecordEntity
import com.finisdiscago.leximvp.core.ui.LexiHeroCard
import com.finisdiscago.leximvp.core.ui.LexiSectionCard
import com.finisdiscago.leximvp.core.ui.LexiStatusPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ExamOcrParser {
    fun draft(document: com.finisdiscago.leximvp.core.document.ImportedDocument): String {
        val numbered = Regex("^\\s*(\\d{1,3})[.)、]\\s*(.*)$")
        val option = Regex("^\\s*([A-H])[.)、]\\s*(.*)$", RegexOption.IGNORE_CASE)
        val blocks = mutableListOf<MutableList<String>>()
        document.chapters.forEach { chapter ->
            chapter.content.lines().forEach { line ->
                if (numbered.matches(line)) blocks += mutableListOf(line) else if (line.isNotBlank()) blocks.lastOrNull()?.add(line)
            }
        }
        if (blocks.isEmpty()) blocks += document.text.lines().filter(String::isNotBlank).toMutableList()
        val questions = JSONArray()
        blocks.forEachIndexed { index, lines ->
            val promptLines = mutableListOf<String>(); val options = JSONArray()
            lines.forEachIndexed { lineIndex, line ->
                val match = option.matchEntire(line)
                if (match != null) options.put("${match.groupValues[1].uppercase()}. ${match.groupValues[2].trim()}")
                else promptLines += if (lineIndex == 0) numbered.matchEntire(line)?.groupValues?.get(2).orEmpty().ifBlank { line } else line
            }
            questions.put(JSONObject().put("id", UUID.randomUUID().toString())
                .put("type", if (options.length() >= 2) "single" else "translation")
                .put("prompt", promptLines.joinToString("\n").trim().ifBlank { "第 ${index + 1} 题（待校正）" })
                .put("options", options).put("answer", "").put("explanation", ""))
        }
        return JSONObject().put("title", document.title).put("passage", "").put("questions", questions)
            .put("source", JSONObject().put("format", document.format).put("assetPath", document.assetPath)
                .put("originalFileName", document.originalFileName).put("warnings", JSONArray(document.warnings)))
            .put("rawOcrText", document.text).toString()
    }
}

object ExamFormat {
    val types = setOf("single", "cloze", "matching", "matching_multi", "translation", "writing")
    fun validate(raw: String): JSONObject {
        val paper = JSONObject(raw)
        require(paper.optString("title").isNotBlank()) { "试卷缺少标题" }
        val questions = paper.optJSONArray("questions") ?: error("试卷缺少 questions 数组")
        require(questions.length() in 1..1000) { "题目数量应为 1–1000" }
        val ids = HashSet<String>()
        for (i in 0 until questions.length()) {
            val q = questions.getJSONObject(i)
            require(q.optString("type") in types) { "第 ${i + 1} 题类型不支持" }
            require(q.optString("prompt").isNotBlank()) { "第 ${i + 1} 题缺少题干" }
            if (!q.has("id")) q.put("id", UUID.randomUUID().toString())
            require(ids.add(q.getString("id"))) { "题目 ID 重复" }
            if (q.optString("type") in setOf("single", "cloze", "matching", "matching_multi")) {
                val options = q.optJSONArray("options") ?: error("第 ${i + 1} 题缺少选项")
                require(options.length() >= 2) { "客观题至少需要两个选项" }
                val values = (0 until options.length()).map { options.getString(it) }
                require(values.distinct().size == values.size) { "选项不能重复" }
                if (q.optString("answer").isNotBlank()) require(q.getString("answer") in values) { "标准答案必须与一个选项一致" }
                if (q.optString("type") == "matching_multi") {
                    val items = q.optJSONArray("matchingItems") ?: error("第 ${i + 1} 题缺少匹配项目")
                    require(items.length() > 0) { "复杂匹配题至少需要一个项目" }
                    for (itemIndex in 0 until items.length()) {
                        val item = items.getJSONObject(itemIndex)
                        require(item.optString("prompt").isNotBlank()) { "第 ${i + 1} 题匹配项目缺少文字" }
                        val expected = item.optJSONArray("answers") ?: JSONArray()
                        require((0 until expected.length()).all { expected.optString(it) in values }) { "匹配答案必须来自选项" }
                    }
                }
            }
        }
        return paper
    }

    fun grade(question: JSONObject, answer: String): Boolean? {
        if (question.optString("type") == "matching_multi") {
            val items = question.optJSONArray("matchingItems") ?: return null
            if ((0 until items.length()).any { (items.optJSONObject(it)?.optJSONArray("answers")?.length() ?: 0) == 0 }) return null
            val actual = runCatching { JSONObject(answer) }.getOrNull() ?: return false
            return (0 until items.length()).all { index ->
                val item = items.getJSONObject(index); val expected = item.getJSONArray("answers").strings().toSet()
                actual.optJSONArray(item.getString("id"))?.strings()?.toSet() == expected
            }
        }
        if (question.optString("type") !in setOf("single", "cloze", "matching")) return null
        val expected = question.optString("answer")
        return if (expected.isBlank()) null else answer.trim() == expected.trim()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExamScreen(app: LexiApplication, onBack: () -> Unit, onOpenWord: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = app.database.lexiconDao()
    val papers by remember { dao.records("exam") }.collectAsState(emptyList())
    val history by remember { dao.records("examAttempt") }.collectAsState(emptyList())
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<WorkspaceRecordEntity?>(null) }
    var answers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var submitted by remember { mutableStateOf(false) }
    var attemptId by rememberSaveable { mutableLongStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var wrongWords by remember { mutableStateOf<Map<String, List<ExamRelatedWord>>>(emptyMap()) }
    var lookupWord by remember { mutableStateOf<String?>(null) }
    var lookupCandidates by remember { mutableStateOf(emptyList<com.finisdiscago.leximvp.domain.model.WordMatchCandidate>()) }
    fun lookup(word: String) { lookupWord = word; scope.launch { lookupCandidates = app.articleRepository.findWordCandidates(word) } }
    suspend fun restoreWrongWords(saved: JSONObject): Map<String, List<ExamRelatedWord>> {
        val stableLinks = saved.optJSONObject("wrongWordStableIds")
        if (stableLinks != null) return stableLinks.keys().asSequence().associateWith { questionId ->
            stableLinks.getJSONArray(questionId).strings().mapNotNull { stableId ->
                app.database.vocabularyDao().getWordByStableId(stableId)?.let { ExamRelatedWord(it.id, it.stableId, it.word) }
            }
        }
        return saved.optJSONObject("wrongWordIds")?.let { legacy ->
            legacy.keys().asSequence().associateWith { questionId ->
                legacy.getJSONArray(questionId).longs().mapNotNull { wordId ->
                    app.database.vocabularyDao().getWord(wordId)?.let { ExamRelatedWord(it.id, it.stableId, it.word) }
                }
            }
        } ?: emptyMap()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) {
                val type = context.contentResolver.getType(uri).orEmpty()
                if (type.contains("json") || type.startsWith("text/")) context.contentResolver.openInputStream(uri)?.use {
                    it.readBytesBounded(8 * 1024 * 1024).toString(Charsets.UTF_8)
                } ?: error("无法打开文件") else ExamOcrParser.draft(app.documentImporter.import(uri))
            } }.onSuccess { draft = it }.onFailure { message = it.message }
        }
    }
    suspend fun persistAttempt(paper: WorkspaceRecordEntity, values: Map<String, String>, done: Boolean) {
        val payload = JSONObject().put("paper", JSONObject(paper.payload)).put("answers", JSONObject(values))
            .put("submitted", done)
        if (done) {
            val links = linkedMapOf<String, List<ExamRelatedWord>>(); val questions = JSONObject(paper.payload).getJSONArray("questions")
            for (index in 0 until questions.length()) {
                val question = questions.getJSONObject(index); val id = question.getString("id")
                if (ExamFormat.grade(question, values[id].orEmpty()) == false) {
                    val optionText = question.optJSONArray("options")?.strings()?.joinToString(" ").orEmpty()
                    val text = listOf(question.optString("prompt"), question.optString("passage"), optionText).joinToString(" ")
                    val wordIds = com.finisdiscago.leximvp.core.text.ArticleTokenizer.tokenize(text).map { it.rawText }.distinct().take(120)
                        .flatMap { app.articleRepository.findWordCandidates(it) }.map { it.wordId }.distinct().take(20)
                    links[id] = wordIds.mapNotNull { wordId ->
                        app.database.vocabularyDao().getWord(wordId)?.let { ExamRelatedWord(it.id, it.stableId, it.word) }
                    }
                }
            }
            wrongWords = links
            payload.put("wrongWordStableIds", JSONObject().apply {
                links.forEach { (id, words) -> put(id, JSONArray(words.map { it.stableId })) }
            })
        }
        attemptId = dao.save(WorkspaceRecordEntity(attemptId, "examAttempt", paper.title, payload.toString(), System.currentTimeMillis()))
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopAppBar(title = { Text(selected?.title ?: "试卷", style = MaterialTheme.typography.headlineSmall) }, navigationIcon = { TextButton(onClick = {
        if (selected != null) selected = null else onBack()
    }) { Text("返回") } }) }) { padding ->
        LazyColumn(Modifier.padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            val paper = selected
            if (paper == null) {
                item { LexiHeroCard(
                    eyebrow = "本地考试空间",
                    title = "${papers.size} 份试卷",
                    supportingText = "导入后先校正题干、选项和答案；客观题自动判分，翻译与写作保存后自评。"
                ) }
                item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { launcher.launch(arrayOf("application/json", "text/*", "application/pdf", "image/*", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("导入试卷") }
                    FilledTonalButton(onClick = { draft = "{\"title\":\"\",\"questions\":[]}" }) { Text("新建") }
                    TextButton(onClick = { showHistory = !showHistory }) { Text("记录") }
                } }
                if (showHistory) items(history, key = { it.id }) { record ->
                    val saved = JSONObject(record.payload)
                    OutlinedButton(onClick = { scope.launch {
                        val snapshot = saved.getJSONObject("paper")
                        selected = WorkspaceRecordEntity(kind = "exam", title = record.title, payload = snapshot.toString(), updatedAtEpochMillis = record.updatedAtEpochMillis)
                        val values = saved.getJSONObject("answers")
                        answers = values.keys().asSequence().associateWith { values.getString(it) }
                        wrongWords = restoreWrongWords(saved)
                        submitted = saved.optBoolean("submitted"); attemptId = record.id
                    } }, modifier = Modifier.fillMaxWidth()) {
                        val snapshot = saved.getJSONObject("paper"); val qs = snapshot.getJSONArray("questions"); val storedAnswers = saved.getJSONObject("answers")
                        val grades = (0 until qs.length()).map { ExamFormat.grade(qs.getJSONObject(it), storedAnswers.optString(qs.getJSONObject(it).getString("id"))) }
                        Text(record.title + if (saved.optBoolean("submitted")) " · ${grades.count { it == true }}/${grades.count { it != null }} · 回顾" else " · 继续作答")
                    }
                }
                items(papers, key = { it.id }) { record ->
                    LexiSectionCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(record.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            LexiStatusPill("本地试卷", highlighted = true)
                        }
                        Row {
                            TextButton(onClick = { selected = record; answers = emptyMap(); wrongWords = emptyMap(); submitted = false; attemptId = 0 }) { Text("开始") }
                            TextButton(onClick = { draft = record.payload }) { Text("校正副本") }
                            TextButton(onClick = { scope.launch { dao.deleteRecord(record.id) } }) { Text("删除试卷") }
                        }
                    } }
                }
            } else {
                val data = JSONObject(paper.payload)
                val questions = data.getJSONArray("questions")
                item { ExamPointText(data.optString("passage"), onWord = ::lookup) }
                if (submitted) item {
                    val results = (0 until questions.length()).map { val q = questions.getJSONObject(it); ExamFormat.grade(q, answers[q.getString("id")].orEmpty()) }
                    Text("客观题 ${results.count { it == true }} / ${results.count { it != null }}；其余题目未自动判分", style = MaterialTheme.typography.titleMedium)
                }
                items(questions.length()) { index ->
                    val q = questions.getJSONObject(index); val id = q.getString("id")
                    LexiSectionCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExamPointText("${index + 1}. ${q.getString("prompt")}", onWord = ::lookup, title = true)
                        q.optString("passage").takeIf { it.isNotBlank() }?.let { ExamPointText(it, onWord = ::lookup) }
                        val options = q.optJSONArray("options")
                        if (q.getString("type") == "matching_multi" && options != null) {
                            val items = q.optJSONArray("matchingItems") ?: JSONArray()
                            val current = runCatching { JSONObject(answers[id].orEmpty()) }.getOrDefault(JSONObject())
                            for (itemIndex in 0 until items.length()) {
                                val matchingItem = items.getJSONObject(itemIndex); val itemId = matchingItem.getString("id")
                                Text(matchingItem.getString("prompt"), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                val chosen = current.optJSONArray(itemId)?.strings()?.toMutableSet() ?: mutableSetOf()
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (optionIndex in 0 until options.length()) {
                                        val option = options.getString(optionIndex)
                                        FilterChip(selected = option in chosen, onClick = {
                                            if (option in chosen) chosen.remove(option) else chosen.add(option)
                                            current.put(itemId, JSONArray(chosen.toList()))
                                            answers = answers + (id to current.toString()); scope.launch { persistAttempt(paper, answers, false) }
                                        }, label = { Text(option) }, enabled = !submitted)
                                    }
                                }
                            }
                        } else if (q.getString("type") in setOf("single", "cloze", "matching") && options != null) {
                            for (i in 0 until options.length()) {
                                val option = options.getString(i)
                                OutlinedButton(onClick = { answers = answers + (id to option); scope.launch { persistAttempt(paper, answers, false) } }, enabled = !submitted, modifier = Modifier.fillMaxWidth()) {
                                    Text((if (answers[id] == option) "✓ " else "") + option)
                                }
                            }
                        } else OutlinedTextField(answers[id].orEmpty(), { value -> answers = answers + (id to value); scope.launch { persistAttempt(paper, answers, false) } }, readOnly = submitted, modifier = Modifier.fillMaxWidth(), label = { Text("作答") }, minLines = 3)
                        if (submitted) {
                            val result = ExamFormat.grade(q, answers[id].orEmpty())
                            Text(when(result) { true -> "正确"; false -> "错误"; null -> "未自动判分 · 对照自评" })
                            Text("参考：${q.optString("answer").ifBlank { "未提供" }}")
                            Text(q.optString("explanation"))
                            if (result == false && wrongWords[id].orEmpty().isNotEmpty()) {
                                Text("相关词条", style = MaterialTheme.typography.labelLarge)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    wrongWords[id].orEmpty().forEach { word -> TextButton({ onOpenWord(word.localId) }) { Text(word.headword) } }
                                }
                            }
                        }
                    } }
                }
                if (!submitted) item { Button(onClick = { scope.launch { persistAttempt(paper, answers, true); submitted = true } }, modifier = Modifier.fillMaxWidth()) { Text("交卷并保存") } }
            }
        }
    }
    draft?.let { initial ->
        ExamCorrectionDialog(initial, onDismiss = { draft = null }, onSave = { corrected -> scope.launch {
            runCatching { val parsed = ExamFormat.validate(corrected); dao.save(WorkspaceRecordEntity(kind = "exam", title = parsed.getString("title"), payload = parsed.toString(), updatedAtEpochMillis = System.currentTimeMillis())) }
                .onSuccess { draft = null }.onFailure { message = it.message }
        } })
    }
    lookupWord?.let { word ->
        AlertDialog(onDismissRequest = { lookupWord = null }, title = { Text(word) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lookupCandidates.isEmpty()) Text("大词库中暂无明确匹配，可建立待完善词条。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                lookupCandidates.forEach { candidate ->
                    FilledTonalButton({ lookupWord = null; onOpenWord(candidate.wordId) }, Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) { Text(candidate.headword); Text(candidate.meaning.ifBlank { "待完善" }, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }, confirmButton = {
            if (lookupCandidates.isEmpty()) Button(onClick = { scope.launch {
                runCatching {
                    val source = app.database.vocabularyDao().getBookByName("试卷生词")?.id ?: app.vocabularyRepository.createBook("试卷生词", "从试卷中收集、等待补充释义的词条")
                    app.vocabularyRepository.createWord(com.finisdiscago.leximvp.domain.repository.WordInput(source, word, ""))
                }.onSuccess { lookupWord = null; onOpenWord(it) }.onFailure { message = it.message }
            } }) { Text("建立待完善词条") } else TextButton({ lookupWord = null }) { Text("关闭") }
        })
    }
}

@Composable
private fun ExamPointText(text: String, onWord: (String) -> Unit, title: Boolean = false) {
    if (text.isBlank()) return
    val wordColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, wordColor) { buildAnnotatedString {
        append(text)
        com.finisdiscago.leximvp.core.text.ArticleTokenizer.tokenize(text).forEach { token ->
            addStringAnnotation("word", token.rawText, token.startOffset, token.endOffset)
            addStyle(SpanStyle(color = wordColor), token.startOffset, token.endOffset)
        }
    } }
    @Suppress("DEPRECATION")
    ClickableText(annotated, style = (if (title) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge).copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset -> annotated.getStringAnnotations("word", offset, offset).firstOrNull()?.item?.let(onWord) })
}

private data class ExamQuestionDraft(val id: String, val type: String, val prompt: String, val passage: String, val options: String, val answer: String, val explanation: String, val matchingItems: String = "")
private data class ExamPaperDraft(val title: String, val passage: String, val questions: List<ExamQuestionDraft>, val source: JSONObject?, val rawOcrText: String) {
    fun toJson(): String = JSONObject().put("title", title).put("passage", passage).put("questions", JSONArray().apply {
        questions.forEach { question -> put(JSONObject().put("id", question.id).put("type", question.type).put("prompt", question.prompt)
            .put("passage", question.passage).put("options", JSONArray(question.options.lines().map(String::trim).filter(String::isNotBlank)))
            .put("answer", question.answer.trim()).put("explanation", question.explanation.trim()).apply {
                if (question.type == "matching_multi") put("matchingItems", JSONArray().apply {
                    question.matchingItems.lines().map(String::trim).filter(String::isNotBlank).forEach { line ->
                        val parts = line.split("=>", limit = 2); val itemPrompt = parts.first().trim(); val itemAnswers = parts.getOrElse(1) { "" }.split('|').map(String::trim).filter(String::isNotBlank)
                        put(JSONObject().put("id", UUID.randomUUID().toString()).put("prompt", itemPrompt).put("answers", JSONArray(itemAnswers)))
                    }
                })
            }) }
    }).apply { source?.let { put("source", it) }; if (rawOcrText.isNotBlank()) put("rawOcrText", rawOcrText) }.toString()
    companion object { fun parse(raw: String): ExamPaperDraft {
        val root = JSONObject(raw); val array = root.optJSONArray("questions") ?: JSONArray()
        val questions = (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { q ->
            val options = q.optJSONArray("options") ?: JSONArray()
            val matching = q.optJSONArray("matchingItems") ?: JSONArray()
            ExamQuestionDraft(q.optString("id").ifBlank { UUID.randomUUID().toString() }, q.optString("type", "translation"), q.optString("prompt"), q.optString("passage"),
                (0 until options.length()).joinToString("\n") { options.optString(it) }, q.optString("answer"), q.optString("explanation"),
                (0 until matching.length()).joinToString("\n") { mi -> val item = matching.getJSONObject(mi); "${item.optString("prompt")} => ${item.optJSONArray("answers")?.strings()?.joinToString("|").orEmpty()}" })
        } }
        return ExamPaperDraft(root.optString("title"), root.optString("passage"), questions, root.optJSONObject("source"), root.optString("rawOcrText"))
    } }
}

@Composable
private fun ExamCorrectionDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var paper by remember(initial) { mutableStateOf(ExamPaperDraft.parse(initial)) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRaw by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(.94f), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("试卷校正", style = MaterialTheme.typography.headlineSmall)
                Text("逐题核对识别结果。答案可留空；未确认时不会自动判分。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(paper.title, { paper = paper.copy(title = it) }, label = { Text("试卷标题") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(paper.passage, { paper = paper.copy(passage = it) }, label = { Text("公共阅读材料（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                        if (paper.rawOcrText.isNotBlank()) TextButton({ showRaw = !showRaw }) { Text(if (showRaw) "收起 OCR 原文" else "对照 OCR 原文") }
                        if (showRaw) Text(paper.rawOcrText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(paper.questions.size, key = { paper.questions[it].id }) { index ->
                        val question = paper.questions[index]
                        LexiSectionCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("第 ${index + 1} 题", style = MaterialTheme.typography.titleMedium)
                                TextButton({ paper = paper.copy(questions = paper.questions.filterIndexed { i, _ -> i != index }) }) { Text("删除") }
                            }
                            TextButton({ val values = ExamFormat.types.toList(); paper = paper.copy(questions = paper.questions.updated(index, question.copy(type = values[(values.indexOf(question.type) + 1).mod(values.size)]))) }) { Text("题型：${question.type}") }
                            OutlinedTextField(question.prompt, { paper = paper.copy(questions = paper.questions.updated(index, question.copy(prompt = it))) }, label = { Text("题干") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            OutlinedTextField(question.passage, { paper = paper.copy(questions = paper.questions.updated(index, question.copy(passage = it))) }, label = { Text("本题材料（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            if (question.type in setOf("single", "cloze", "matching")) OutlinedTextField(question.options, { paper = paper.copy(questions = paper.questions.updated(index, question.copy(options = it))) }, label = { Text("选项，每行一个") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                            if (question.type == "matching_multi") {
                                OutlinedTextField(question.options, { paper = paper.copy(questions = paper.questions.updated(index, question.copy(options = it))) }, label = { Text("候选选项，每行一个") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                                OutlinedTextField(question.matchingItems, { paper = paper.copy(questions = paper.questions.updated(index, question.copy(matchingItems = it))) }, label = { Text("匹配项目：每行 项目 => 选项|选项") }, modifier = Modifier.fillMaxWidth(), minLines = 4)
                            }
                            OutlinedTextField(question.answer, { paper = paper.copy(questions = paper.questions.updated(index, question.copy(answer = it))) }, label = { Text("标准/参考答案（可留空）") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(question.explanation, { paper = paper.copy(questions = paper.questions.updated(index, question.copy(explanation = it))) }, label = { Text("解析（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            Row { TextButton({ if (index > 0) paper = paper.copy(questions = paper.questions.swapped(index, index - 1)) }) { Text("上移") }; TextButton({ if (index < paper.questions.lastIndex) paper = paper.copy(questions = paper.questions.swapped(index, index + 1)) }) { Text("下移") } }
                        }
                    }
                    item { OutlinedButton({ paper = paper.copy(questions = paper.questions + ExamQuestionDraft(UUID.randomUUID().toString(), "translation", "", "", "", "", "")) }, Modifier.fillMaxWidth()) { Text("添加题目") } }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(onClick = { runCatching { ExamFormat.validate(paper.toJson()).toString() }.onSuccess(onSave).onFailure { error = it.message } }) { Text("确认导入") }
                }
            }
        }
    }
}

private fun <T> List<T>.updated(index: Int, value: T) = toMutableList().also { it[index] = value }
private fun <T> List<T>.swapped(first: Int, second: Int) = toMutableList().also { val value = it[first]; it[first] = it[second]; it[second] = value }
private data class ExamRelatedWord(val localId: Long, val stableId: String, val headword: String)

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if(count < 0) break; require(output.size() + count <= limit) { "文件超过 8 MB 限制" }; output.write(buffer, 0, count) }
    return output.toByteArray()
}
private fun JSONArray.strings(): List<String> = (0 until length()).map { optString(it) }
private fun JSONArray.longs(): List<Long> = (0 until length()).map { optLong(it) }
