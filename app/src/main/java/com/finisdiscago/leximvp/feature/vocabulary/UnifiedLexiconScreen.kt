package com.finisdiscago.leximvp.feature.vocabulary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.finisdiscago.leximvp.LexiApplication
import com.finisdiscago.leximvp.core.database.StudyCardFactory
import com.finisdiscago.leximvp.core.database.LexicalContentLifecycleService
import com.finisdiscago.leximvp.core.database.entity.WorkspaceRecordEntity
import com.finisdiscago.leximvp.core.text.ModuleRegistry
import com.finisdiscago.leximvp.core.text.ModulePayloadCodec
import com.finisdiscago.leximvp.core.text.ContentMergeService
import com.finisdiscago.leximvp.core.text.RichWordContentParser
import com.finisdiscago.leximvp.core.ui.LexiSectionCard
import com.finisdiscago.leximvp.core.ui.LexiStatusPill
import com.finisdiscago.leximvp.core.ui.LexiHeroCard
import com.finisdiscago.leximvp.domain.repository.WordInput
import com.finisdiscago.leximvp.domain.model.ContentModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UnifiedLexiconScreen(app: LexiApplication, openWord: (Long) -> Unit, openReading: () -> Unit, openExams: () -> Unit, manageBooks: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf("source") }
    var sourceId by rememberSaveable { mutableLongStateOf(0L) }
    var tagCategory by rememberSaveable { mutableStateOf("") }
    var tagValue by rememberSaveable { mutableStateOf("") }
    var visibleLimit by rememberSaveable { mutableIntStateOf(60) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var showViews by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = app.database.lexiconDao()
    val rows by remember(query, status, order, sourceId, tagCategory, tagValue, visibleLimit) {
        dao.page(query.trim(), status, sourceId, tagCategory, tagValue, order, visibleLimit, 0)
    }.collectAsState(emptyList())
    val tagOptions by remember { dao.tagOptions() }.collectAsState(emptyList())
    val frequencyRecordCount by remember { dao.frequencyRecordCount() }.collectAsState(0)
    val total by remember { dao.count() }.collectAsState(0)
    val views by remember { dao.records("view") }.collectAsState(emptyList())
    val books by app.vocabularyRepository.observeBooks().collectAsState(emptyList())
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            message = "正在导入内容…"
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        app.vocabularyPackageImporter.import(input) { progress -> message = "已处理 ${progress.importedEntries} / ${progress.totalEntries}" }
                    } ?: error("无法打开文件")
                }
            }.onSuccess { message = "已更新 ${it.entryCount} 个词条，重复词合并为同一学习记录" }
                .onFailure { message = it.message ?: "导入失败" }
        }
    }

    var loadingStarter by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { com.finisdiscago.leximvp.core.ui.LexiTopBar("词库", actions = {
            IconButton(onClick = manageBooks) { Icon(Icons.Default.FolderOpen, "管理词书") }
            IconButton(onClick = { importer.launch(arrayOf("*/*")) }) { Icon(Icons.Default.FileUpload, "导入内容包") }
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, "添加词条") }
        }) }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            item {
                Column {

            if (total == 0 && query.isBlank()) {
                FilledTonalButton(enabled = !loadingStarter, onClick = {
                    loadingStarter = true
                    scope.launch {
                        try {
                            val result = withContext(Dispatchers.IO) { context.assets.open("starter.lexipack").use { app.vocabularyPackageImporter.import(it) } }
                            message = "已添加 ${result.entryCount} 个示例词条，返回学习页开始体验"
                        } catch (cancelled: kotlinx.coroutines.CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            message = error.message ?: "示例导入失败，请重试"
                        } finally { loadingStarter = false }
                    }
                }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Text(if (loadingStarter) "正在准备示例" else "体验示例词库") }
            }
            Text("$total 个词条 · 同一单词共享学习进度", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(true, {}, { Text("词汇 $total") })
                FilterChip(false, openReading, { Text("阅读") })
                FilterChip(false, openExams, { Text("试卷") })
            }
            OutlinedTextField(
                query,
                { query = it; visibleLimit = 60 },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { IconButton(onClick = { showFilters = true }) { Icon(Icons.Default.Tune, "筛选和排序") } },
                placeholder = { Text("搜索词头、释义或标签") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(22.dp),
                singleLine = true
            )
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(sourceId == 0L, { sourceId = 0L; visibleLimit = 60 }, { Text("全部来源") }) }
                items(books, key = { it.id }) { book ->
                    FilterChip(sourceId == book.id, { sourceId = book.id; visibleLimit = 60 }, { Text("${book.name} ${book.wordCount}") })
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(filterSummary(status, order, tagValue), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (views.isNotEmpty()) TextButton(onClick = { showViews = true }) { Text("已保存 ${views.size}") }
                TextButton(onClick = {
                    scope.launch { dao.save(WorkspaceRecordEntity(kind = "view", title = query.ifBlank { "词库视图" }, payload = JSONObject().put("query", query).put("status", status).put("order", order).put("sourceId", sourceId).put("tagCategory", tagCategory).put("tagValue", tagValue).toString(), updatedAtEpochMillis = System.currentTimeMillis())) }
                }) { Text("保存视图") }
            }
            message?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                }
            }
                items(rows, key = { it.id }) { word ->
                    LexiSectionCard(Modifier.padding(horizontal = 16.dp).clickable { openWord(word.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(word.word, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text(word.meaning.ifBlank { "待完善 · 暂不生成练习" }, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    LexiStatusPill(statusLabel(word.intakeStatus), highlighted = word.intakeStatus == "Learning")
                                    LexiStatusPill("熟知度 ${(word.proficiencyScore * 100).toInt()}%")
                                    word.frequencyRank?.let { LexiStatusPill("${word.frequencyCorpus.orEmpty()} #$it") }
                                        ?: word.frequencyPerMillion?.let { LexiStatusPill("${word.frequencyCorpus.orEmpty()} ${"%.1f".format(it)}/百万") }
                                    contentPrevalence(word.richContentJson)?.let { LexiStatusPill(it) }
                                }
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                if (rows.isEmpty()) item { Box(Modifier.fillParentMaxHeight(.6f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("没有匹配的词条", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                if (rows.size == visibleLimit) item {
                    OutlinedButton(onClick = { visibleLimit += 60 }, Modifier.fillMaxWidth()) { Text("继续加载") }
                }
        }
    }

    if (showFilters) FilterDialog(status, order, tagCategory, tagValue, tagOptions, frequencyRecordCount > 0, { status = it }, { order = it }, { category, value -> tagCategory = category; tagValue = value }, { showFilters = false; visibleLimit = 60 })
    if (showViews) SavedViewsDialog(views, onSelect = { view ->
        val data = JSONObject(view.payload); query = data.optString("query"); status = data.optString("status"); order = data.optString("order", "source"); sourceId = data.optLong("sourceId"); tagCategory = data.optString("tagCategory"); tagValue = data.optString("tagValue"); visibleLimit = 60; showViews = false
    }, onDelete = { scope.launch { dao.deleteRecord(it) } }, onDismiss = { showViews = false })
    if (showAdd) AddWordDialog(onDismiss = { showAdd = false }, onSave = { word, meaning ->
        scope.launch {
            runCatching {
                val source = app.database.vocabularyDao().getBookByName("个人添加")?.id ?: app.vocabularyRepository.createBook("个人添加", "手工添加与阅读收集")
                app.vocabularyRepository.createWord(WordInput(source, word, meaning, tags = if (meaning.isBlank()) listOf("待完善") else emptyList()))
            }.onSuccess { showAdd = false; openWord(it) }.onFailure { message = it.message }
        }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LexicalEntryScreen(app: LexiApplication, wordId: Long, onBack: () -> Unit) {
    var word by remember { mutableStateOf<com.finisdiscago.leximvp.core.database.entity.WordEntryEntity?>(null) }
    var sourceName by remember { mutableStateOf("") }
    var wordForms by remember { mutableStateOf(emptyList<com.finisdiscago.leximvp.core.database.entity.WordFormEntity>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var editor by rememberSaveable { mutableStateOf(false) }
    var addModule by rememberSaveable { mutableStateOf(false) }
    var menu by rememberSaveable { mutableStateOf(false) }
    var arrangeModules by rememberSaveable { mutableStateOf(false) }
    var editingModule by remember { mutableStateOf<ContentModule?>(null) }
    var deletingModule by remember { mutableStateOf<ContentModule?>(null) }
    var manageSenses by rememberSaveable { mutableStateOf(false) }
    var senseRevision by remember { mutableIntStateOf(0) }
    var senses by remember { mutableStateOf(emptyList<com.finisdiscago.leximvp.core.database.entity.SenseEntity>()) }
    val scope = rememberCoroutineScope()
    val lexiconDao = app.database.lexiconDao()
    val defaultLayoutRecords by remember { lexiconDao.records("module_layout_default") }.collectAsState(emptyList())
    val defaultLayout = defaultLayoutRecords.firstOrNull()?.payload
    LaunchedEffect(wordId, senseRevision) {
        word = app.database.vocabularyDao().getWord(wordId)
        sourceName = word?.let { app.database.vocabularyDao().getBook(it.bookId)?.name }.orEmpty()
        wordForms = app.database.vocabularyDao().getWordForms(wordId)
        senses = app.database.vocabularyDao().getSensesForWord(wordId)
    }
    val current = word
    val rich = remember(current?.richContentJson) { RichWordContentParser.parseOrNull(current?.richContentJson) }
    val modules = remember(current?.richContentJson, defaultLayout) { ModuleRegistry.read(current?.richContentJson, defaultLayout) }
    val importConflicts = remember(current?.richContentJson) { ContentMergeService.conflicts(current?.richContentJson) }
    fun saveRaw(raw: String) { scope.launch {
        runCatching {
            JSONObject(raw)
            val updated = requireNotNull(word).copy(richContentJson = raw, updatedAtEpochMillis = System.currentTimeMillis())
            app.database.vocabularyDao().updateWord(updated); word = updated
        }.onFailure { message = it.message }
    } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("完整词条") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton(onClick = { current?.word?.let(app.audioService::speak) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "播放读音") }
                    Box { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "更多") }; DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("编辑释义") }, { editor = true; menu = false }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem({ Text("管理结构化义项") }, { manageSenses = true; menu = false }, leadingIcon = { Icon(Icons.Default.Checklist, null) })
                        DropdownMenuItem({ Text("添加自定义模块") }, { addModule = true; menu = false }, leadingIcon = { Icon(Icons.Default.Add, null) })
                        DropdownMenuItem({ Text(if (arrangeModules) "完成模块调整" else "调整模块布局") }, { arrangeModules = !arrangeModules; menu = false }, leadingIcon = { Icon(Icons.Default.ViewAgenda, null) })
                        DropdownMenuItem({ Text("将当前布局设为词库默认") }, {
                            scope.launch {
                                val timestamp = System.currentTimeMillis()
                                val payload = ModuleRegistry.defaultLayout(modules)
                                val record = defaultLayoutRecords.firstOrNull()?.copy(payload = payload, updatedAtEpochMillis = timestamp)
                                    ?: WorkspaceRecordEntity(kind = "module_layout_default", title = "词库默认模块布局", payload = payload, updatedAtEpochMillis = timestamp)
                                lexiconDao.save(record); message = "已保存词库默认布局"; menu = false
                            }
                        }, leadingIcon = { Icon(Icons.Default.Save, null) })
                    } }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(current?.word ?: "…", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
                    val pronunciation = rich?.pronunciation?.let { listOf(it.ukIpa, it.usIpa).filter(String::isNotBlank).joinToString("  ") }.orEmpty().ifBlank { current?.pronunciation.orEmpty() }
                    if (pronunciation.isNotBlank()) Text(pronunciation, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        LexiStatusPill(sourceName.ifBlank { "来源未标明" })
                        LexiStatusPill(if (current?.meaning.isNullOrBlank()) "待完善" else "已有释义", highlighted = current?.meaning?.isNotBlank() == true)
                    }
                }
            }
            if (current != null && rich == null) item {
                LexiSectionCard {
                    Text("释义", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(current.meaning.ifBlank { "这个词条已建立，但还没有释义。完善后才会生成练习。" }, style = MaterialTheme.typography.titleLarge)
                    current.exampleSentence?.let { Text(it) }
                    current.exampleTranslation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (current.meaning.isBlank()) FilledTonalButton(onClick = { editor = true }) { Text("完善词条") }
                }
            }
            modules.filter { arrangeModules || !it.hidden }.forEach { module ->
                item(key = module.id) {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        if (arrangeModules) ModuleLayoutControls(module, onChange = { order, hidden, collapsed ->
                            val raw = current?.richContentJson ?: "{}"
                            saveRaw(ModuleRegistry.layout(raw, module.id, order, hidden, collapsed))
                        }, onEdit = if (module.id.startsWith("legacy:")) null else ({ editingModule = module }), onDelete = if (module.id.startsWith("legacy:")) null else ({ deletingModule = module }))
                        if (module.hidden) {
                            LexiSectionCard { Text("${module.title} · 已隐藏", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        } else if (module.id.startsWith("legacy:") && rich != null && module.type in RICH_MODULE_TYPES) {
                            RichWordModule(rich, module.type, module.collapsed)
                        } else {
                            LexiSectionCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(module.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    LexiStatusPill(if (module.status == "user") "个人内容" else module.status)
                                }
                                module.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                if (!module.collapsed) ModuleContentRenderer(module)
                            }
                        }
                    }
                }
            }
            if (wordForms.isNotEmpty()) item {
                LexiSectionCard {
                    Text("词形变体", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(wordForms.joinToString(" · ") { it.form }, style = MaterialTheme.typography.bodyLarge)
                    Text("这些形式均指向当前规范词头，不会建立第二套学习记录。", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (importConflicts.isNotEmpty()) item {
                LexiSectionCard {
                    Text("待处理导入冲突", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("当前内容不会被静默覆盖。逐项选择保留当前内容或采用新导入内容。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    importConflicts.forEach { conflict ->
                        Text(conflict.fields.joinToString("、"), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton({ current?.richContentJson?.let { saveRaw(ContentMergeService.resolve(it, conflict.index, false)) } }) { Text("保留当前") }
                            Button({ current?.richContentJson?.let { saveRaw(ContentMergeService.resolve(it, conflict.index, true)) } }) { Text("采用导入") }
                        }
                    }
                }
            }
            item {
                LexiSectionCard {
                    Text("熟知度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ scope.launch { app.vocabularyRepository.setWordFamiliarity(wordId, "Unknown"); message = "已安排重学" } }, Modifier.weight(1f)) { Text("不认识") }
                        OutlinedButton({ scope.launch { app.vocabularyRepository.setWordFamiliarity(wordId, "Mastered"); message = "已标记熟知" } }, Modifier.weight(1f)) { Text("熟知") }
                        TextButton({ scope.launch { app.vocabularyRepository.setWordFamiliarity(wordId, null); message = "已恢复系统评估" } }) { Text("系统评估") }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
    }

    if (editor && current != null) EditWordDialog(current.word, current.meaning, current.pronunciation.orEmpty(), onDismiss = { editor = false }, onSave = { headword, meaning, pronunciation ->
        scope.launch {
            runCatching {
                app.vocabularyRepository.updateWord(wordId, WordInput(current.bookId, headword, meaning, pronunciation = pronunciation, tags = current.tags?.split(',').orEmpty()))
                val updated = app.database.vocabularyDao().getWord(wordId) ?: return@runCatching
                if (meaning.isNotBlank()) StudyCardFactory(app.database).ensure(wordId, System.currentTimeMillis())
                word = updated; editor = false
            }.onFailure { message = it.message }
        }
    })
    if (addModule) AddModuleDialog(onDismiss = { addModule = false }, onSave = { type, title, content -> saveRaw(ModuleRegistry.add(current?.richContentJson, type, title, content)); addModule = false })
    editingModule?.let { module ->
        AddModuleDialog(initial = module, onDismiss = { editingModule = null }, onSave = { _, title, content ->
            current?.richContentJson?.let { saveRaw(ModuleRegistry.update(it, module.id, title, content)) }
            editingModule = null
        })
    }
    deletingModule?.let { module ->
        AlertDialog(onDismissRequest = { deletingModule = null }, title = { Text("删除“${module.title}”？") },
            text = { Text("此操作只删除这个自定义模块，不影响词条、学习记录或其它模块。") },
            confirmButton = { Button(onClick = { current?.richContentJson?.let { saveRaw(ModuleRegistry.remove(it, module.id)) }; deletingModule = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingModule = null }) { Text("取消") } })
    }
    if (manageSenses) SenseManagerDialog(
        senses = senses,
        onToggle = { sense ->
            scope.launch {
                LexicalContentLifecycleService(app.database).setSenseActive(sense.stableId, !sense.active)
                senseRevision++
                message = if (sense.active) "义项已停用，关联练习已暂停，历史记录已保留" else "义项与关联练习已恢复"
            }
        },
        onDismiss = { manageSenses = false }
    )
}

@Composable
private fun SenseManagerDialog(
    senses: List<com.finisdiscago.leximvp.core.database.entity.SenseEntity>,
    onToggle: (com.finisdiscago.leximvp.core.database.entity.SenseEntity) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("管理结构化义项") },
        text = {
            if (senses.isEmpty()) Text("当前词条还没有独立结构化义项。") else LazyColumn {
                items(senses, key = { it.stableId }) { sense ->
                    ListItem(
                        headlineContent = { Text(sense.meaningZh.ifBlank { sense.definitionEn.ifBlank { "未填写释义" } }) },
                        supportingContent = { Text(listOf(sense.partOfSpeech, if (sense.active) "使用中" else "已停用").filter(String::isNotBlank).joinToString(" · ")) },
                        trailingContent = {
                            TextButton(onClick = { onToggle(sense) }) { Text(if (sense.active) "停用" else "恢复") }
                        }
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("完成") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(status: String, order: String, tagCategory: String, tagValue: String, tagOptions: List<com.finisdiscago.leximvp.core.database.dao.LexiconTagOption>, hasFrequencyEvidence: Boolean, onStatus: (String) -> Unit, onOrder: (String) -> Unit, onTag: (String, String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("筛选与排序") }, text = { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("学习状态", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { listOf("" to "全部", "Pending" to "待选", "Queued" to "待学习", "Learning" to "学习中", "Mature" to "成熟").forEach { (value, label) -> FilterChip(status == value, { onStatus(value) }, { Text(label) }) } }
        Text("内容标签", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            FilterChip(tagCategory.isEmpty(), { onTag("", "") }, { Text("全部") })
            tagOptions.take(18).forEach { option ->
                FilterChip(tagCategory == option.category && tagValue == option.value, { onTag(option.category, option.value) }, { Text("${tagLabel(option.category)} · ${option.value} ${option.count}") })
            }
        }
        Text("排序", fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            (listOf("source" to "来源顺序", "alphabet" to "字母序", "familiarity" to "熟知度") + if (hasFrequencyEvidence) listOf("corpus_frequency" to "语料频次") else emptyList()).forEach { (value, label) -> FilterChip(order == value, { onOrder(value) }, { Text(label) }) }
        }
    } }, confirmButton = { Button(onClick = onDismiss) { Text("完成") } })
}

@Composable
private fun SavedViewsDialog(views: List<WorkspaceRecordEntity>, onSelect: (WorkspaceRecordEntity) -> Unit, onDelete: (Long) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("已保存视图") }, text = { LazyColumn { items(views, key = { it.id }) { view -> ListItem({ Text(view.title) }, modifier = Modifier.clickable { onSelect(view) }, trailingContent = { IconButton({ onDelete(view.id) }) { Icon(Icons.Default.Delete, "删除") } }) } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
private fun AddWordDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var word by rememberSaveable { mutableStateOf("") }; var meaning by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("添加词条") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(word, { word = it }, label = { Text("单词或表达") }, singleLine = true)
        OutlinedTextField(meaning, { meaning = it }, label = { Text("释义（可留空，标记为待完善）") }, minLines = 2)
    } }, confirmButton = { Button({ onSave(word, meaning) }, enabled = word.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun EditWordDialog(initialWord: String, initialMeaning: String, initialPronunciation: String, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var word by rememberSaveable { mutableStateOf(initialWord) }; var meaning by rememberSaveable { mutableStateOf(initialMeaning) }; var pronunciation by rememberSaveable { mutableStateOf(initialPronunciation) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("编辑核心内容") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(word, { word = it }, label = { Text("词头") })
        OutlinedTextField(pronunciation, { pronunciation = it }, label = { Text("音标") })
        OutlinedTextField(meaning, { meaning = it }, label = { Text("释义") }, minLines = 3)
        Text("结构化模块保持原样，不会被此次编辑覆盖。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } }, confirmButton = { Button({ onSave(word, meaning, pronunciation) }, enabled = word.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun AddModuleDialog(initial: ContentModule? = null, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var title by rememberSaveable(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }; var type by rememberSaveable(initial?.id) { mutableStateOf(initial?.type ?: "text") }; var content by rememberSaveable(initial?.id) { mutableStateOf(initial?.let { ModulePayloadCodec.decodeForEditor(it.type, it.payload) }.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "添加个人模块" else "编辑个人模块") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, { title = it }, label = { Text("标题") })
        if (initial == null) TextButton({ val types = ModuleRegistry.customTypes.toList(); type = types[(types.indexOf(type) + 1) % types.size] }) { Text("内容类型：$type") } else Text("内容类型：$type", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(content, { content = it }, label = { Text(ModulePayloadCodec.editorHint(type)) }, minLines = 4)
    } }, confirmButton = { Button({ onSave(type, title.ifBlank { "个人笔记" }, content) }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun ModuleLayoutControls(module: ContentModule, onChange: (Int, Boolean, Boolean) -> Unit, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp)) {
            Text(module.title, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ onChange(module.order - 10, module.hidden, module.collapsed) }) { Icon(Icons.Default.ArrowUpward, "上移") }
                IconButton({ onChange(module.order + 10, module.hidden, module.collapsed) }) { Icon(Icons.Default.ArrowDownward, "下移") }
                IconButton({ onChange(module.order, !module.hidden, module.collapsed) }) { Icon(if (module.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff, if (module.hidden) "显示" else "隐藏") }
                IconButton({ onChange(module.order, module.hidden, !module.collapsed) }) { Icon(if (module.collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess, if (module.collapsed) "展开" else "折叠") }
                onEdit?.let { IconButton(it) { Icon(Icons.Default.Edit, "编辑模块") } }
                onDelete?.let { IconButton(it) { Icon(Icons.Default.Delete, "删除模块") } }
            }
        }
    }
}

private fun filterSummary(status: String, order: String, tagValue: String): String = listOf(statusLabel(status), tagValue.takeIf(String::isNotBlank), when (order) { "alphabet" -> "字母序"; "familiarity" -> "熟知度"; "corpus_frequency" -> "有来源语料频次"; else -> "来源顺序" }).filterNotNull().joinToString(" · ")
private fun tagLabel(category: String): String = when (category) { "exam" -> "考试"; "topic" -> "主题"; "domain" -> "领域"; "register" -> "语域"; "content_status" -> "完整度"; else -> category }
private fun statusLabel(status: String): String = when (status) { "" -> "全部状态"; "Pending" -> "待选"; "Queued" -> "待学习"; "Learning", "Reviewing" -> "学习中"; "Mature" -> "成熟"; else -> status }
private fun contentPrevalence(raw: String?): String? = when {
    raw.isNullOrBlank() -> null
    "very_common" in raw -> "内容标注·非常常见"
    "less_common" in raw -> "内容标注·较少见"
    "\"frequency\":\"common\"" in raw -> "内容标注·常见"
    "\"frequency\":\"rare\"" in raw -> "内容标注·罕见"
    else -> null
}

private val RICH_MODULE_TYPES = setOf("pronunciation", "senses", "coreMeaning", "etymology", "wordFormation", "confusables", "semanticNetwork", "chunks")
