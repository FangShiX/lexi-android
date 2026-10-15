package com.finisdiscago.leximvp.feature.reading

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finisdiscago.leximvp.core.audio.AudioService
import com.finisdiscago.leximvp.core.audio.AudioAvailability
import com.finisdiscago.leximvp.core.audio.userMessage
import com.finisdiscago.leximvp.core.ui.LexiTestTags
import com.finisdiscago.leximvp.core.ui.LexiHeroCard
import com.finisdiscago.leximvp.core.ui.LexiSectionCard
import com.finisdiscago.leximvp.core.ui.LexiStatusPill
import com.finisdiscago.leximvp.domain.model.Article
import com.finisdiscago.leximvp.domain.model.ArticleReading
import com.finisdiscago.leximvp.domain.model.ArticleToken
import com.finisdiscago.leximvp.domain.model.ArticleWordStatus
import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.repository.ArticleRepository
import com.finisdiscago.leximvp.domain.repository.VocabularyRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONObject
import android.graphics.BitmapFactory
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListRoute(
    repository: ArticleRepository,
    onBackToBooks: () -> Unit,
    onOpenArticle: (Long) -> Unit,
    modifier: Modifier = Modifier,
    documentImporter: com.finisdiscago.leximvp.core.document.LocalDocumentImporter? = null,
    viewModel: ArticleListViewModel = viewModel(
        factory = ArticleListViewModel.Factory(repository)
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Article?>(null) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && documentImporter != null) scope.launch {
            importStatus = "正在本地解析…"
            runCatching {
                val document = documentImporter.import(uri) { done, total ->
                    importStatus = "正在解析 $done / $total"
                }
                if (document.format == "EPUB" && document.chapters.size > 1) {
                    var firstArticleId = 0L
                    document.chapters.asReversed().forEachIndexed { index, chapter ->
                        importStatus = "正在建立可点击正文 ${index + 1} / ${document.chapters.size}"
                        val articleId = repository.createArticle(
                            "${document.title} · ${chapter.title}",
                            chapter.content,
                            documentSourceJson(document, chapter.locator, chapter.images)
                        )
                        if (chapter === document.chapters.first()) firstArticleId = articleId
                    }
                    firstArticleId
                } else repository.createArticle(
                    document.title,
                    document.text,
                    documentSourceJson(document, document.chapters.firstOrNull()?.locator, document.images)
                )
            }.onSuccess {
                importStatus = null
                onOpenArticle(it)
            }.onFailure {
                importStatus = null
                val message = it.message ?: "文档导入失败"
                scope.launch { snackbar.showSnackbar(message) }
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("阅读", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    TextButton(onClick = onBackToBooks) { Text("词库") }
                },
                actions = {
                    if (documentImporter != null) {
                        TextButton(onClick = {
                            documentLauncher.launch(arrayOf(
                                "text/*", "application/epub+zip", "application/pdf",
                                "image/*", "application/octet-stream"
                            ))
                        }) { Text("导入文档") }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showEditor = true }) {
                Icon(Icons.Default.Add, contentDescription = "新建文章")
            }
        }
    ) { padding ->
        if (state.articles.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("还没有文章", style = MaterialTheme.typography.titleLarge)
                    Text(importStatus ?: "粘贴文章，或导入 EPUB、PDF、图片、TXT/HTML 开始取词。")
                    Button(onClick = { showEditor = true }) { Text("新建文章") }
                }
            }
        } else {
            val groups = state.articles.groupBy { it.documentName() }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    LexiHeroCard(
                        eyebrow = "本地阅读空间",
                        title = "${groups.size} 本 · ${state.articles.size} 章",
                        supportingText = importStatus ?: "正文里的每个英文词都可点击；库外词一键建立待完善词条。"
                    )
                }
                groups.forEach { (documentName, chapters) ->
                    item(key = documentName) {
                        var expanded by rememberSaveable(documentName) { mutableStateOf(chapters.size <= 4) }
                        LexiSectionCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(documentName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                    Text("${chapters.size} 章 · 本地保存", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                LexiStatusPill(if (chapters.first().source?.contains("EPUB") == true) "EPUB" else "文章", highlighted = true)
                            }
                            chapters.take(if (expanded) chapters.size else 3).forEachIndexed { index, article ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    Modifier.fillMaxWidth().clickable { onOpenArticle(article.id) }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(article.title.substringAfter(" · "), style = MaterialTheme.typography.titleMedium)
                                        Text(article.content.take(70).replace('\n', ' '), maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { deleting = article }) { Icon(Icons.Default.Delete, "删除文章") }
                                }
                            }
                            if (chapters.size > 3) TextButton(onClick = { expanded = !expanded }) {
                                Text(if (expanded) "收起章节" else "查看全部 ${chapters.size} 章")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ArticleEditorDialog(
            onDismiss = { showEditor = false },
            onSave = { title, content ->
                viewModel.createArticle(title, content) {
                    showEditor = false
                    onOpenArticle(it)
                }
            }
        )
    }
    deleting?.let { article ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除“${article.title}”？") },
            text = { Text("文章和阅读标记将被删除，词库内容不会受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteArticle(article.id)
                    deleting = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleReaderRoute(
    articleId: Long,
    articleRepository: ArticleRepository,
    vocabularyRepository: VocabularyRepository,
    audioService: AudioService,
    settingsRepository: com.finisdiscago.leximvp.domain.repository.SettingsRepository,
    onBack: () -> Unit,
    onStartLearning: () -> Unit,
    modifier: Modifier = Modifier,
    onStartCapturedLearning: ((List<Long>) -> Unit)? = null,
    viewModel: ArticleReaderViewModel = viewModel(
        factory = ArticleReaderViewModel.Factory(
            articleId,
            articleRepository,
            vocabularyRepository,
            audioService
        )
    )
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = com.finisdiscago.leximvp.domain.model.UserSettings())
    val snackbar = remember { SnackbarHostState() }
    var showMarks by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var showNote by rememberSaveable { mutableStateOf(false) }
    var showCorrection by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.reading?.article?.title ?: "阅读") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showCorrection = true }) { Icon(Icons.Default.Edit, "校正正文") }
                    IconButton(onClick = viewModel::addBookmark) { Icon(Icons.Default.BookmarkAdd, "在当前位置添加书签") }
                    IconButton(onClick = { showMarks = true }) { Icon(Icons.Default.Notes, "书签与标注") }
                    TextButton(onClick = {
                        if (onStartCapturedLearning != null && state.capturedWordIds.isNotEmpty()) onStartCapturedLearning(state.capturedWordIds.toList()) else onStartLearning()
                    }) { Text("学习生词 ${state.capturedWordIds.size}") }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.reading == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text(state.errorMessage ?: "文章不存在") }

            else -> Column(Modifier.padding(padding).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    val night = settings.themeMode == com.finisdiscago.leximvp.domain.model.ThemeMode.Night
                    Text(if (night) "夜间阅读" else "日间阅读", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                settingsRepository.setThemeMode(if (night) com.finisdiscago.leximvp.domain.model.ThemeMode.Day else com.finisdiscago.leximvp.domain.model.ThemeMode.Night)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                snackbar.showSnackbar("无法保存主题，请重试")
                            }
                        }
                    }) { Text(if (night) "切换日间" else "切换夜间") }
                }
                ReadingText(
                reading = requireNotNull(state.reading),
                onTokenClick = viewModel::selectToken,
                onPositionChanged = viewModel::saveReadingPosition,
                jumpOffset = state.jumpOffset,
                onJumpHandled = viewModel::consumeJump,
                fontSizeSp = settings.readerFontSizeSp,
                lineHeightSp = settings.effectiveReaderLineHeightSp,
                modifier = Modifier.weight(1f)
            )
            }
        }
    }

    state.selectedToken?.let { token ->
        TokenDialog(
            token = token,
            books = state.books,
            audioReady = state.audioReady,
            audioAvailability = state.audioAvailability,
            onDismiss = viewModel::dismissToken,
            onSpeak = viewModel::speakSelected,
            onKnown = viewModel::markKnown,
            onUnknown = viewModel::markUnknown,
            onAddUnknown = viewModel::addUnknownWord,
            onAddPending = viewModel::addPendingWord,
            candidates = state.wordCandidates,
            onChooseCandidate = viewModel::chooseCandidate
        )
    }
    if (showMarks) ReadingMarksDialog(
        marks = state.marks,
        onAddNote = { showMarks = false; showNote = true },
        onOpen = { showMarks = false; viewModel.jumpToMark(it) },
        onDelete = viewModel::deleteMark,
        onDismiss = { showMarks = false }
    )
    if (showNote) AddReadingNoteDialog(onSave = { viewModel.addNote(it); showNote = false }, onDismiss = { showNote = false })
    if (showCorrection) CorrectReadingTextDialog(
        initial = state.reading?.article?.content.orEmpty(),
        onSave = { viewModel.correctContent(it); showCorrection = false },
        onDismiss = { showCorrection = false }
    )
}

@Composable
private fun CorrectReadingTextDialog(initial: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var content by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("校正识别文本") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("保存后会重新分词并关联词库；已有的学习标记会按规范词保留。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(content, { content = it }, modifier = Modifier.heightIn(min = 280.dp), minLines = 12)
        }
    }, confirmButton = { Button(onClick = { onSave(content) }, enabled = content.isNotBlank()) { Text("保存并重新识别词汇") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun ReadingText(
    reading: ArticleReading,
    onTokenClick: (ArticleToken) -> Unit,
    onPositionChanged: (Int) -> Unit,
    jumpOffset: Int?,
    onJumpHandled: () -> Unit,
    fontSizeSp: Int,
    lineHeightSp: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val images = remember(reading.article.source) {
        runCatching {
            val array = JSONObject(reading.article.source.orEmpty()).optJSONArray("images") ?: org.json.JSONArray()
            (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { it.optString("path") to it.optString("alt") } }
        }.getOrDefault(emptyList())
    }
    val blocks = remember(reading.article.content, images) { reading.readerBlocks(images) }
    val initialIndex = remember(reading.article.id) {
        blocks.indexOfLast { it.startOffset <= reading.article.lastReadOffset }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    LaunchedEffect(reading.article.id, blocks) {
        snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collectLatest { index ->
            kotlinx.coroutines.delay(450)
            blocks.getOrNull(index)?.startOffset?.let(onPositionChanged)
        }
    }
    LaunchedEffect(jumpOffset) {
        jumpOffset?.let { offset ->
            listState.animateScrollToItem(blocks.indexOfLast { it.startOffset <= offset }.coerceAtLeast(0))
            onJumpHandled()
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(blocks) { _, block ->
            when (block) {
                is ReaderBlock.TextBlock -> {
                    val annotated = reading.toAnnotatedString(MaterialTheme.colorScheme, block.start, block.end)
                    @Suppress("DEPRECATION")
                    ClickableText(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSizeSp.sp, lineHeight = lineHeightSp.sp, color = MaterialTheme.colorScheme.onBackground),
                        onClick = { offset -> annotated.getStringAnnotations("token", offset, offset).firstOrNull()?.item?.toLongOrNull()?.let { id -> reading.tokens.firstOrNull { it.id == id } }?.let(onTokenClick) }
                    )
                }
                is ReaderBlock.ImageBlock -> {
                    val bitmap = remember(block.path) { BitmapFactory.decodeFile(File(context.filesDir, block.path).absolutePath) }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = block.alt.ifBlank { "EPUB 插图" },
                            modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingMarksDialog(
    marks: List<com.finisdiscago.leximvp.domain.model.ReadingMark>,
    onAddNote: () -> Unit,
    onOpen: (Int) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("书签与标注") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (marks.isEmpty()) Text("当前章节还没有标记。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            marks.take(12).forEach { mark ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(mark.offset) }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (mark.kind == "note") mark.note.ifBlank { "标注" } else "书签 · 位置 ${mark.offset}", fontWeight = FontWeight.SemiBold)
                        Text(mark.snippet.ifBlank { "无预览" }, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton({ onDelete(mark.id) }) { Icon(Icons.Default.Delete, "删除") }
                }
            }
            TextButton(onClick = onAddNote) { Text("标注当前位置") }
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text("完成") } })
}

@Composable
private fun AddReadingNoteDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("标注当前位置") }, text = {
        OutlinedTextField(note, { note = it }, label = { Text("笔记") }, minLines = 3)
    }, confirmButton = { Button(onClick = { onSave(note) }, enabled = note.isNotBlank()) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

private fun Article.documentName(): String = runCatching {
    JSONObject(source.orEmpty()).optString("originalFileName").substringBeforeLast('.').ifBlank { title }
}.getOrDefault(title)

private fun documentSourceJson(
    document: com.finisdiscago.leximvp.core.document.ImportedDocument,
    locator: String?,
    images: List<com.finisdiscago.leximvp.core.document.ImportedImage>
): String = JSONObject()
    .put("format", document.format)
    .put("assetPath", document.assetPath)
    .put("originalFileName", document.originalFileName)
    .put("mimeType", document.mimeType)
    .put("locator", locator)
    .put("warnings", document.warnings)
    .put("images", org.json.JSONArray().apply {
        images.forEach { image -> put(JSONObject().put("path", image.localPath).put("alt", image.alt)) }
    })
    .toString()

private sealed interface ReaderBlock {
    data class TextBlock(val start: Int, val end: Int) : ReaderBlock
    data class ImageBlock(val path: String, val alt: String, val offset: Int) : ReaderBlock
}

private val ReaderBlock.startOffset: Int get() = when (this) {
    is ReaderBlock.TextBlock -> start
    is ReaderBlock.ImageBlock -> offset
}

private fun ArticleReading.readerBlocks(images: List<Pair<String, String>>): List<ReaderBlock> {
    val result = mutableListOf<ReaderBlock>(); var start = 0; var imageIndex = 0
    fun appendParagraphs(from: Int, to: Int) {
        if (to <= from) return
        val text = article.content
        var paragraphStart = from
        var cursor = from
        while (cursor < to - 1) {
            if (text[cursor] == '\n' && text[cursor + 1] == '\n') {
                if (cursor > paragraphStart) result += ReaderBlock.TextBlock(paragraphStart, cursor)
                cursor += 2
                while (cursor < to && text[cursor] == '\n') cursor++
                paragraphStart = cursor
            } else cursor++
        }
        if (paragraphStart < to) result += ReaderBlock.TextBlock(paragraphStart, to)
    }
    article.content.forEachIndexed { index, char ->
        if (char == '\uFFFC') {
            appendParagraphs(start, index)
            images.getOrNull(imageIndex++)?.let { result += ReaderBlock.ImageBlock(it.first, it.second, index) }
            start = index + 1
        }
    }
    appendParagraphs(start, article.content.length)
    return result.ifEmpty { listOf(ReaderBlock.TextBlock(0, article.content.length)) }
}

private fun ArticleReading.toAnnotatedString(colors: androidx.compose.material3.ColorScheme, rangeStart: Int = 0, rangeEnd: Int = article.content.length): AnnotatedString = buildAnnotatedString {
    append(article.content.substring(rangeStart, rangeEnd))
    tokens.filter { it.startOffset >= rangeStart && it.endOffset <= rangeEnd }.forEach { token ->
        val start = token.startOffset - rangeStart
        val end = token.endOffset - rangeStart
        addStringAnnotation(
            tag = "token",
            annotation = token.id.toString(),
            start = start,
            end = end
        )
        addStyle(
            colors.readerTokenStyle(token.status),
            start,
            end
        )
    }
}

@Composable
private fun ArticleEditorDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var content by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建文章") },
        text = {
            Column(
                modifier = Modifier.imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("粘贴英文正文") },
                    modifier = Modifier.heightIn(min = 180.dp),
                    minLines = 8
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() && content.isNotBlank(),
                onClick = { onSave(title, content) }
            ) { Text("保存并阅读") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TokenDialog(
    token: ArticleToken,
    books: List<VocabularyBook>,
    audioReady: Boolean,
    audioAvailability: AudioAvailability = if (audioReady) {
        AudioAvailability.Ready
    } else {
        AudioAvailability.InitializationFailed
    },
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onKnown: () -> Unit,
    onUnknown: () -> Unit,
    onAddUnknown: (Long, String) -> Unit,
    onAddPending: (() -> Unit)? = null,
    candidates: List<com.finisdiscago.leximvp.domain.model.WordMatchCandidate> = emptyList(),
    onChooseCandidate: (Long) -> Unit = {}
) {
    var meaning by rememberSaveable(token.id) { mutableStateOf("") }
    var selectedBook by rememberSaveable(token.id, books) {
        mutableStateOf(books.firstOrNull()?.id)
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().imePadding().padding(horizontal = 22.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(token.rawText, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    token.pronunciation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                IconButton(enabled = audioReady, onClick = onSpeak) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "播放读音")
                }
            }
            audioAvailability.userMessage()?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            token.meaning?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
            LexiStatusPill(statusLabel(token.status), highlighted = token.wordId != null)
            if (token.wordId == null) {
                if (candidates.isNotEmpty()) {
                    Text("找到多个可能的规范词条，请确认：", fontWeight = FontWeight.SemiBold)
                    candidates.take(6).forEach { candidate ->
                        FilledTonalButton(onClick = { onChooseCandidate(candidate.wordId) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(candidate.headword, fontWeight = FontWeight.SemiBold)
                                Text(candidate.meaning.ifBlank { "待完善" }, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider()
                }
                if (onAddPending != null) {
                    Text("本地词典没有这个词。加入后先创建待完善词条，并保存原文词形；补充可靠释义前不会生成练习。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("识别原型：${token.normalizedText}", style = MaterialTheme.typography.labelLarge)
                } else if (books.isEmpty()) {
                    Text("请先创建词库，再添加这个单词。")
                } else {
                    books.forEach { book ->
                        Row(Modifier.fillMaxWidth().clickable { selectedBook = book.id }.background(if (selectedBook == book.id) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(10.dp)) { Text(book.name) }
                    }
                    OutlinedTextField(meaning, { meaning = it }, label = { Text("中文 / 概念") }, modifier = Modifier.fillMaxWidth().testTag(LexiTestTags.ReadingMeaning))
                }
            }
            if (token.wordId == null) {
                Button(
                    enabled = onAddPending != null || (selectedBook != null && meaning.isNotBlank()),
                    onClick = { if (onAddPending != null) onAddPending() else onAddUnknown(requireNotNull(selectedBook), meaning) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag(LexiTestTags.ReadingAddUnknown)
                ) { Text(if (onAddPending != null) "加入待完善词条" else "加入待学习") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = onKnown, modifier = Modifier.weight(1f)) { Text("认识") }
                    Button(onClick = onUnknown, modifier = Modifier.weight(1f)) { Text("不认识 · 加入学习") }
                }
            }
        }
    }
}

private fun statusLabel(status: ArticleWordStatus): String = when (status) {
    ArticleWordStatus.Unknown -> "未加入词库"
    ArticleWordStatus.Unlearned -> "未学习"
    ArticleWordStatus.Learning -> "学习中"
    ArticleWordStatus.Learned -> "已学习"
    ArticleWordStatus.Known -> "本篇已标记认识"
}
