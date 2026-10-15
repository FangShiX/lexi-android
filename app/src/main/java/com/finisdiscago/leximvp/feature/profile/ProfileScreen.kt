package com.finisdiscago.leximvp.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finisdiscago.leximvp.core.ui.LexiTopBar
import com.finisdiscago.leximvp.core.ui.SettingsMenuLink
import com.finisdiscago.leximvp.domain.model.ThemeMode
import com.finisdiscago.leximvp.domain.model.UserSettings

@Composable
internal fun ProfileScreen(settings: UserSettings, onOpenSettings: () -> Unit, onOpenBooks: () -> Unit,
                           onOpenReading: () -> Unit, onOpenStatistics: () -> Unit,
                           onAdjustPlan: () -> Unit = onOpenSettings, onOpenBackup: () -> Unit = onOpenSettings) {
    Scaffold(topBar = { LexiTopBar("我的") {
        IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, "打开设置") }
    } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center) {
                        Text("L", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("每一天，都有积累", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Lexi · 我的学习空间", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("我的学习计划", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(Modifier.fillMaxWidth()) {
                            GoalMetric("每日新词", settings.dailyNewWordLimit, Modifier.weight(1f))
                            GoalMetric("每日复习", settings.dailyReviewLimit, Modifier.weight(1f))
                            IconButton(onClick = onAdjustPlan) { Icon(Icons.Default.Tune, "调整学习计划", tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("学习工具", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
                    ProfileMenu(Icons.Default.CollectionsBookmark, "我的词库", "词书、单词与学习计划", onOpenBooks)
                    ProfileMenu(Icons.AutoMirrored.Filled.MenuBook, "阅读空间", "读文章，收集生词", onOpenReading)
                    ProfileMenu(Icons.Default.BarChart, "学习统计", "查看学习记录与进度", onOpenStatistics)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    ProfileMenu(Icons.Default.Tune, "偏好设置", if (settings.themeMode == ThemeMode.Night) "夜间模式 · 阅读 · 发音" else "日间模式 · 阅读 · 发音", onOpenSettings)
                    ProfileMenu(Icons.Default.Backup, "备份与恢复", "保存完整学习数据", onOpenBackup)
                }
            }
            item { Text("Lexi ${com.finisdiscago.leximvp.BuildConfig.VERSION_NAME}", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
    }
}

@Composable
private fun GoalMetric(label: String, value: Int, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun ProfileMenu(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    SettingsMenuLink(title = { Text(title, fontWeight = FontWeight.Medium) }, subtitle = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        icon = { Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        } }, action = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }, onClick = onClick)
}
