package com.finisdiscago.leximvp.feature.profile

import android.Manifest
import android.database.sqlite.SQLiteFullException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import com.finisdiscago.leximvp.core.ui.LexiTopBar
import com.finisdiscago.leximvp.domain.model.ThemeMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finisdiscago.leximvp.core.backup.BackupFormatException
import com.finisdiscago.leximvp.core.backup.LexiBackupService
import com.finisdiscago.leximvp.core.reminder.ReminderScheduler
import com.finisdiscago.leximvp.core.ui.LexiTestTags
import com.finisdiscago.leximvp.core.ui.LexiHeroCard
import com.finisdiscago.leximvp.core.ui.LexiSectionCard
import com.finisdiscago.leximvp.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    repository: SettingsRepository,
    reminderScheduler: ReminderScheduler,
    backupService: LexiBackupService,
    onOpenVocabulary: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    initialSection: Int = 0,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(repository, reminderScheduler)
    )
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedSection by rememberSaveable(initialSection) { mutableStateOf(initialSection.coerceIn(0, 2)) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var busyOperation by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.setReminderEnabled(granted)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busyOperation = "正在导出完整备份"
        scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt").use { output ->
                        requireNotNull(output) { "无法创建备份文件" }
                        backupService.export(output)
                    }
                }
                snackbar.showSnackbar(
                    "备份完成：${summary.books} 个词库、${summary.words} 个词条"
                )
            } catch (_: CancellationException) {
                throw CancellationException("Backup export cancelled")
            } catch (error: Exception) {
                snackbar.showSnackbar(error.backupUserMessage("导出"))
            } finally {
                busyOperation = null
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingRestoreUri = uri
    }

    fun startRestore(uri: android.net.Uri) {
        pendingRestoreUri = null
        busyOperation = "正在校验并恢复备份"
        scope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "无法读取备份文件" }
                        backupService.restore(input)
                    }
                }
                val restoredSettings = repository.settings.first()
                if (restoredSettings.reminderEnabled) {
                    reminderScheduler.schedule(
                        restoredSettings.reminderHour,
                        restoredSettings.reminderMinute
                    )
                } else {
                    reminderScheduler.cancel()
                }
                snackbar.showSnackbar(
                    "恢复完成：${summary.words} 个词条、${summary.attempts} 条学习记录"
                )
            } catch (_: CancellationException) {
                throw CancellationException("Backup restore cancelled")
            } catch (error: Exception) {
                snackbar.showSnackbar(error.backupUserMessage("恢复"))
            } finally {
                busyOperation = null
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { LexiTopBar("设置", onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    listOf("外观", "学习", "数据").forEachIndexed { index, label ->
                        SegmentedButton(selected = selectedSection == index, onClick = { selectedSection = index },
                            shape = SegmentedButtonDefaults.itemShape(index, 3), label = { Text(label) })
                    }
                }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedSection == 1) item {
                SettingsCard("每日任务") {
                    StepperRow(
                        "每日新词",
                        settings.dailyNewWordLimit,
                        onDecrease = {
                            viewModel.setDailyNewLimit(settings.dailyNewWordLimit - 5)
                        },
                        onIncrease = {
                            viewModel.setDailyNewLimit(settings.dailyNewWordLimit + 5)
                        }
                    )
                    StepperRow(
                        "每日复习",
                        settings.dailyReviewLimit,
                        onDecrease = {
                            viewModel.setDailyReviewLimit(settings.dailyReviewLimit - 10)
                        },
                        onIncrease = {
                            viewModel.setDailyReviewLimit(settings.dailyReviewLimit + 10)
                        }
                    )
                }
            }
            if (selectedSection == 1) item {
                SettingsCard("发音") {
                    ToggleRow(
                        label = "自动播放读音",
                        checked = settings.autoPlayAudio,
                        onCheckedChange = viewModel::setAutoPlay
                    )
                    Text("语速 ${(settings.speechRate * 100).toInt()}%")
                    Slider(
                        value = settings.speechRate,
                        onValueChange = viewModel::setSpeechRate,
                        valueRange = 0.5f..1.5f,
                        steps = 9
                    )
                }
            }
            if (selectedSection == 1) item {
                SettingsCard("提醒") {
                    ToggleRow(
                        label = "每日学习提醒",
                        checked = settings.reminderEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.setReminderEnabled(enabled)
                            }
                        }
                    )
                    StepperRow(
                        label = "提醒小时",
                        value = settings.reminderHour,
                        onDecrease = {
                            viewModel.setReminderTime(
                                (settings.reminderHour + 23) % 24,
                                settings.reminderMinute
                            )
                        },
                        onIncrease = {
                            viewModel.setReminderTime(
                                (settings.reminderHour + 1) % 24,
                                settings.reminderMinute
                            )
                        },
                        valueText = "%02d:%02d".format(
                            settings.reminderHour,
                            settings.reminderMinute
                        )
                    )
                    StepperRow(
                        label = "提醒分钟",
                        value = settings.reminderMinute,
                        onDecrease = {
                            viewModel.setReminderTime(
                                settings.reminderHour,
                                (settings.reminderMinute + 55) % 60
                            )
                        },
                        onIncrease = {
                            viewModel.setReminderTime(
                                settings.reminderHour,
                                (settings.reminderMinute + 5) % 60
                            )
                        },
                        valueText = "每 5 分钟"
                    )
                }
            }
            if (selectedSection == 0) item {
                SettingsCard("外观与阅读") {
                    Text("选择你喜欢的阅读环境", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemePreview(ThemeMode.Day, settings.themeMode == ThemeMode.Day, Modifier.weight(1f)) { viewModel.setThemeMode(ThemeMode.Day) }
                        ThemePreview(ThemeMode.Night, settings.themeMode == ThemeMode.Night, Modifier.weight(1f)) { viewModel.setThemeMode(ThemeMode.Night) }
                    }
                    Text("正文字号 ${settings.readerFontSizeSp} sp")
                    Slider(value = settings.readerFontSizeSp.toFloat(), onValueChange = { viewModel.setReaderFontSize(it.toInt()) }, valueRange = 15f..28f, steps = 12)
                    Text("行高 ${settings.effectiveReaderLineHeightSp} sp")
                    Slider(value = settings.effectiveReaderLineHeightSp.toFloat(), onValueChange = { viewModel.setReaderLineHeight(it.toInt()) }, valueRange = maxOf(22, settings.readerFontSizeSp + 6).toFloat()..44f)
                    FilledTonalButton(onClick = onOpenVocabulary, modifier = Modifier.fillMaxWidth()) { Text("打开词库并调整模块布局") }
                    Text("在词条菜单中调整模块顺序与显示。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (selectedSection == 2) item {
                DataSafetyCard(
                    enabled = busyOperation == null,
                    onExport = {
                        exportLauncher.launch(
                            "lexi-backup-${LocalDate.now()}.lexibackup"
                        )
                    },
                    onRestore = {
                        restoreLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream")
                        )
                    }
                )
            }
            if (selectedSection == 2) item {
                SettingsCard("来源与许可") {
                    Text("导入前请确认内容来源与使用许可。资料仅在设备上处理，不上传到开发者服务器。")
                    TextButton(onClick = { showHelp = true }) { Text("使用帮助、隐私与开源许可") }
                }
            }
        }
        }
    }

    if (showHelp) HelpDialog { showHelp = false }
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("替换全部本地数据？") },
            text = {
                Text("恢复会先校验整个备份。校验通过后，当前词库、学习记录、文章和设置将被备份内容替换。")
            },
            confirmButton = {
                TextButton(onClick = { startRestore(uri) }) {
                    Text("确认恢复", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) {
                    Text("取消")
                }
            }
        )
    }

    busyOperation?.let { operation ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(operation) },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("请勿关闭应用。恢复失败时原有数据库不会被部分覆盖。")
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
internal fun DataSafetyCard(
    enabled: Boolean,
    onExport: () -> Unit,
    onRestore: () -> Unit
) {
    SettingsCard("数据安全") {
        Text(
            "备份包含词库、学习进度、复习记录、文章和当前设置。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LexiTestTags.BackupExport),
            enabled = enabled,
            onClick = onExport
        ) {
            Icon(Icons.Default.Download, contentDescription = null)
            Text("导出完整备份", modifier = Modifier.padding(start = 8.dp))
        }
        FilledTonalButton(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LexiTestTags.BackupRestore),
            enabled = enabled,
            onClick = onRestore
        ) {
            Icon(Icons.Default.Upload, contentDescription = null)
            Text("从备份恢复", modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun Throwable.backupUserMessage(operation: String): String = when (this) {
    is BackupFormatException -> message ?: "备份格式无效"
    is SQLiteFullException -> "设备存储空间不足，无法完成$operation"
    is SecurityException -> "没有访问该文件的权限，请重新选择"
    is FileNotFoundException -> "所选文件已移动或不可访问"
    is IOException -> "文件读写失败，请检查存储空间和文件完整性"
    else -> message?.takeIf(String::isNotBlank) ?: "${operation}失败，请重试"
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun StepperRow(
    label: String,
    value: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    valueText: String = value.toString()
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = onDecrease) {
            Icon(Icons.Default.Remove, contentDescription = "减少$label")
        }
        Text(valueText)
        IconButton(onClick = onIncrease) {
            Icon(Icons.Default.Add, contentDescription = "增加$label")
        }
    }
}

@Composable
private fun ThemePreview(mode: ThemeMode, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val night = mode == ThemeMode.Night
    val background = if (night) androidx.compose.ui.graphics.Color(0xFF19201F) else androidx.compose.ui.graphics.Color(0xFFF4F7F6)
    val foreground = if (night) androidx.compose.ui.graphics.Color(0xFFE2EBE7) else androidx.compose.ui.graphics.Color(0xFF17211F)
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), color = background,
        border = androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Aa", style = MaterialTheme.typography.headlineMedium, color = foreground)
            Text(if (night) "夜间模式" else "日间模式", style = MaterialTheme.typography.labelLarge, color = foreground)
            Text(if (selected) "正在使用" else "点击切换", style = MaterialTheme.typography.labelSmall, color = foreground)
        }
    }
}
