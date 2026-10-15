package com.finisdiscago.leximvp.feature.vocabulary

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finisdiscago.leximvp.core.database.VocabularyImportProgress
import com.finisdiscago.leximvp.core.database.VocabularyPackageException
import com.finisdiscago.leximvp.core.database.VocabularyPackageImporter
import com.finisdiscago.leximvp.core.ui.LexiTestTags
import com.finisdiscago.leximvp.core.ui.LexiPageTitle
import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.model.WordEntry
import com.finisdiscago.leximvp.domain.repository.VocabularyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListRoute(
    repository: VocabularyRepository,
    packageImporter: VocabularyPackageImporter,
    onOpenBook: (Long) -> Unit,
    onOpenArticles: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: BookListViewModel = viewModel(factory = BookListViewModel.Factory(repository))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingBook by remember { mutableStateOf<VocabularyBook?>(null) }
    var planningBook by remember { mutableStateOf<VocabularyBook?>(null) }
    var deletingBook by remember { mutableStateOf<VocabularyBook?>(null) }
    val importing = state.importState is VocabularyImportUiState.Running
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        viewModel.startImport { onProgress ->
            withContext(Dispatchers.IO) {
                context.applicationContext.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取所选词包" }
                    packageImporter.import(input) { progress ->
                        onProgress(progress)
                    }
                }
            }
        }
    }

    fun launchPackagePicker() {
        if (!importing) {
            packagePicker.launch(
                arrayOf(
                    "application/zip",
                    "application/octet-stream"
                )
            )
        }
    }

    fun cancelImport() {
        viewModel.cancelImport()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(state.importState) {
        when (val importState = state.importState) {
            is VocabularyImportUiState.Success -> {
                snackbarHostState.showSnackbar(
                    buildString {
                        append("已导入 ${importState.result.bookName}，新增 ")
                        append("${importState.result.entryCount} 词")
                        if (importState.result.skippedDuplicateCount > 0) {
                            append("，未变 ${importState.result.skippedDuplicateCount} 词")
                        }
                        if (importState.result.updatedCount > 0) {
                            append("，补充 ${importState.result.updatedCount} 词")
                        }
                        if (importState.result.conflictCount > 0) {
                            append("，${importState.result.conflictCount} 处冲突待处理")
                        }
                    }
                )
                viewModel.acknowledgeImportResult()
            }
            VocabularyImportUiState.Cancelled -> {
                snackbarHostState.showSnackbar("已取消导入，未保存任何词包数据")
                viewModel.acknowledgeImportResult()
            }
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            com.finisdiscago.leximvp.core.ui.LexiTopBar("管理词书", onBack, actions = {
                TextButton(enabled = !importing, onClick = onOpenArticles) { Text("阅读") }
                IconButton(enabled = !importing, onClick = ::launchPackagePicker) {
                    Icon(Icons.Default.FileOpen, contentDescription = "导入词包")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.books.isNotEmpty() && !importing) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "新建词库")
                }
            }
        }
    ) { innerPadding ->
        if (state.books.isEmpty()) {
            EmptyContent(
                title = "还没有词库",
                message = "导入现成词包可以立即开始学习，也可以创建空词库手动添加。",
                actionLabel = "导入词包",
                actionIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                onAction = ::launchPackagePicker,
                secondaryActionLabel = "新建空词库",
                onSecondaryAction = { showCreateDialog = true },
                modifier = Modifier.padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    VocabularyOverviewCard(
                        learnedCount = state.learnedVocabularyCount,
                        totalCount = state.totalVocabularyCount
                    )
                }
                items(state.books, key = { it.id }) { book ->
                    BookRow(
                        book = book,
                        onClick = { onOpenBook(book.id) },
                        onEdit = { editingBook = book },
                        onPlan = { planningBook = book },
                        onDelete = { deletingBook = book }
                    )
                }
            }
        }
    }

    if (importing) {
        VocabularyImportDialog(
            progress = (state.importState as VocabularyImportUiState.Running).progress,
            onCancel = ::cancelImport
        )
    }
    (state.importState as? VocabularyImportUiState.Failed)?.let { failed ->
        VocabularyImportFailureDialog(
            message = failed.message,
            onDismiss = viewModel::acknowledgeImportResult,
            onRetry = {
                viewModel.acknowledgeImportResult()
                launchPackagePicker()
            }
        )
    }

    if (showCreateDialog) {
        BookEditorDialog(
            title = "新建词库",
            onDismiss = { showCreateDialog = false },
            onSave = { name, description ->
                viewModel.createBook(name, description) { showCreateDialog = false }
            }
        )
    }
    editingBook?.let { book ->
        BookEditorDialog(
            title = "编辑词库",
            initialName = book.name,
            initialDescription = book.description,
            onDismiss = { editingBook = null },
            onSave = { name, description ->
                viewModel.updateBook(book.id, name, description) { editingBook = null }
            }
        )
    }
    planningBook?.let { book ->
        BookPlanDialog(
            book = book,
            onDismiss = { planningBook = null },
            onSave = { enabled, limit, priority ->
                viewModel.updateBookPlan(book.id, enabled, limit, priority) {
                    planningBook = null
                }
            }
        )
    }
    deletingBook?.let { book ->
        DeleteDialog(
            title = "删除“${book.name}”？",
            message = "词库中的词条、卡片和学习记录将一并删除。",
            onDismiss = { deletingBook = null },
            onConfirm = {
                viewModel.deleteBook(book.id)
                deletingBook = null
            }
        )
    }
}

@Composable
private fun VocabularyImportDialog(
    progress: VocabularyImportProgress?,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在安全导入词包") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("正在复制并校验词包，尚未写入学习数据。")
                } else {
                    val fraction = progress.importedEntries.toFloat() /
                        progress.totalEntries.coerceAtLeast(1)
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "已处理 ${progress.importedEntries} / ${progress.totalEntries} 个词条"
                    )
                    Text(
                        "取消或发生错误时，本次导入会整体回滚。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("取消导入")
            }
        }
    )
}

@Composable
internal fun VocabularyImportFailureDialog(
    message: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("词包导入失败") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, color = MaterialTheme.colorScheme.error)
                Text("本次导入未保存任何半成品数据，可以重新选择文件后再试。")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onRetry,
                modifier = Modifier.testTag(LexiTestTags.ImportRetry)
            ) {
                Text("重新选择词包")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordListRoute(
    bookId: Long,
    repository: VocabularyRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WordListViewModel = viewModel(
        factory = WordListViewModel.Factory(bookId, repository)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingWord by remember { mutableStateOf<WordEntry?>(null) }
    var detailedWord by remember { mutableStateOf<WordEntry?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var deletingWord by remember { mutableStateOf<WordEntry?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.book?.name ?: "词库",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加词条")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索单词或释义") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                TextButton(onClick = { sortMenuExpanded = true }) {
                    Icon(Icons.Default.Tune, contentDescription = null)
                    Text("排序：${state.sort.label}", modifier = Modifier.padding(start = 6.dp))
                }
                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    WordSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            onClick = {
                                viewModel.updateSort(sort)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
            if (state.words.isEmpty()) {
                EmptyContent(
                    title = if (state.query.isBlank()) "还没有词条" else "没有匹配结果",
                    message = if (state.query.isBlank()) {
                        "添加单词后会自动生成三种基础复习卡片。"
                    } else {
                        "尝试使用其他单词或释义搜索。"
                    },
                    actionLabel = if (state.query.isBlank()) "添加词条" else null,
                    onAction = { showCreateDialog = true },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.words, key = { it.id }) { word ->
                        WordRow(
                            word = word,
                            onOpen = {
                                if (word.richContentJson != null) {
                                    detailedWord = word
                                } else {
                                    editingWord = word
                                }
                            },
                            onEdit = { editingWord = word },
                            onSetFamiliarity = { familiarity ->
                                viewModel.setFamiliarity(word.id, familiarity)
                            },
                            onDelete = { deletingWord = word }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        WordEditorDialog(
            title = "添加词条",
            onDismiss = { showCreateDialog = false },
            onSave = { draft ->
                viewModel.saveWord(null, draft) { showCreateDialog = false }
            }
        )
    }
    editingWord?.let { word ->
        WordEditorDialog(
            title = "编辑词条",
            initialDraft = WordDraft.from(word),
            onDismiss = { editingWord = null },
            onSave = { draft ->
                viewModel.saveWord(word.id, draft) { editingWord = null }
            }
        )
    }
    deletingWord?.let { word ->
        DeleteDialog(
            title = "删除“${word.word}”？",
            message = "该词条对应的复习卡片和学习状态也会删除。",
            onDismiss = { deletingWord = null },
            onConfirm = {
                viewModel.deleteWord(word.id)
                deletingWord = null
            }
        )
    }
    detailedWord?.let { word ->
        com.finisdiscago.leximvp.core.text.RichWordContentParser
            .parseOrNull(word.richContentJson)
            ?.let { content ->
                RichWordDetailDialog(
                    content = content,
                    onDismiss = { detailedWord = null }
                )
            }
    }
}

@Composable
private fun BookRow(
    book: VocabularyBook,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onPlan: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription =
                    "${book.name}，已学 ${book.learnedWordCount}，未学 ${book.pendingWordCount}"
            }
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(book.name, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "已学 ${book.learnedWordCount} · 未学 ${book.pendingWordCount}" +
                        book.description.takeIf(String::isNotBlank)?.let { " · $it" }.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { book.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (book.selectionEnabled) {
                        "每日选词 ${book.dailySelectionLimit} · 优先级 ${book.selectionPriority}"
                    } else {
                        "已暂停每日选词"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "词库菜单")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("每日计划") },
                        leadingIcon = { Icon(Icons.Default.Tune, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onPlan()
                        }
                    )
                    if (!book.isProtected) DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun WordRow(
    word: WordEntry,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onSetFamiliarity: (String?) -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = word.word,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            word.pronunciation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = word.meaning,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "熟知度：${familiarityLabel(word.manualFamiliarity)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "词条菜单")
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onEdit()
                    }
                )
                listOf(
                    "Unknown" to "标记为不熟",
                    "Learning" to "标记为学习中",
                    "Familiar" to "标记为熟悉",
                    "Mastered" to "标记为已掌握"
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menuExpanded = false
                            onSetFamiliarity(value)
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun VocabularyOverviewCard(
    learnedCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "我的词汇量 $learnedCount，共收录 $totalCount"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("我的词汇量", style = MaterialTheme.typography.titleMedium)
            Text(
                learnedCount.toString(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "已学习 $learnedCount / 共收录 $totalCount",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LinearProgressIndicator(
                progress = {
                    if (totalCount == 0) 0f else learnedCount.toFloat() / totalCount
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun BookPlanDialog(
    book: VocabularyBook,
    onDismiss: () -> Unit,
    onSave: (Boolean, Int, Int) -> Unit
) {
    var enabled by rememberSaveable(book.id) { mutableStateOf(book.selectionEnabled) }
    var limit by rememberSaveable(book.id) {
        mutableStateOf(book.dailySelectionLimit.toString())
    }
    var priority by rememberSaveable(book.id) {
        mutableStateOf(book.selectionPriority.toString())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("每日计划 · ${book.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("参与每日选词", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                OutlinedTextField(
                    value = limit,
                    onValueChange = { limit = it.filter(Char::isDigit).take(3) },
                    label = { Text("每天选入数量（0–200）") },
                    enabled = enabled,
                    singleLine = true
                )
                OutlinedTextField(
                    value = priority,
                    onValueChange = {
                        priority = it.filterIndexed { index, char ->
                            char.isDigit() || (char == '-' && index == 0)
                        }.take(4)
                    },
                    label = { Text("优先级（-100–100，越大越优先）") },
                    enabled = enabled,
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        enabled,
                        limit.toIntOrNull()?.coerceIn(0, 200) ?: 0,
                        priority.toIntOrNull()?.coerceIn(-100, 100) ?: 0
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun familiarityLabel(value: String?): String = when (value) {
    "Unknown" -> "不熟"
    "Learning" -> "学习中"
    "Familiar" -> "熟悉"
    "Mastered" -> "已掌握"
    else -> "未标记"
}

@Composable
private fun BookEditorDialog(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("说明（可选）") },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, description) }
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun WordEditorDialog(
    title: String,
    initialDraft: WordDraft = WordDraft(),
    onDismiss: () -> Unit,
    onSave: (WordDraft) -> Unit
) {
    var word by rememberSaveable { mutableStateOf(initialDraft.word) }
    var meaning by rememberSaveable { mutableStateOf(initialDraft.meaning) }
    var pronunciation by rememberSaveable { mutableStateOf(initialDraft.pronunciation) }
    var exampleSentence by rememberSaveable { mutableStateOf(initialDraft.exampleSentence) }
    var exampleTranslation by rememberSaveable {
        mutableStateOf(initialDraft.exampleTranslation)
    }
    var tags by rememberSaveable { mutableStateOf(initialDraft.tags) }
    val draft = WordDraft(
        word = word,
        meaning = meaning,
        pronunciation = pronunciation,
        exampleSentence = exampleSentence,
        exampleTranslation = exampleTranslation,
        tags = tags
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = draft.word,
                        onValueChange = { word = it },
                        label = { Text("单词") },
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.meaning,
                        onValueChange = { meaning = it },
                        label = { Text("中文 / 概念") },
                        minLines = 2
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.pronunciation,
                        onValueChange = { pronunciation = it },
                        label = { Text("音标（可选）") },
                        singleLine = true
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.exampleSentence,
                        onValueChange = { exampleSentence = it },
                        label = { Text("例句（可选）") },
                        minLines = 2
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.exampleTranslation,
                        onValueChange = { exampleTranslation = it },
                        label = { Text("例句翻译（可选）") },
                        minLines = 2
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.tags,
                        onValueChange = { tags = it },
                        label = { Text("标签，以逗号分隔（可选）") },
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = draft.word.isNotBlank() && draft.meaning.isNotBlank(),
                onClick = { onSave(draft) }
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun DeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EmptyContent(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: (@Composable () -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {}
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actionLabel?.let {
                Button(onClick = onAction) {
                    actionIcon?.invoke()
                        ?: Icon(Icons.Default.Add, contentDescription = null)
                    Text(it, modifier = Modifier.padding(start = 8.dp))
                }
            }
            secondaryActionLabel?.let {
                TextButton(onClick = onSecondaryAction) {
                    Text(it)
                }
            }
        }
    }
}
