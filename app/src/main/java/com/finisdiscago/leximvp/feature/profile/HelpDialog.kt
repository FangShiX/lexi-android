package com.finisdiscago.leximvp.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.finisdiscago.leximvp.BuildConfig

@Composable
internal fun HelpDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val licenses = remember {
        context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { name ->
            context.assets.open("licenses/$name").bufferedReader().use { it.readText() }
        }
    }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Lexi ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("开始使用", style = MaterialTheme.typography.titleMedium)
                Text("在词库中体验示例内容、添加词条或导入 .lexipack。设置每日计划后，从学习页开始新词学习；到期词进入复习。阅读中点词可加入待学习，试卷支持结构化导入、客观判分和主观自评。")
                Text("阅读与发音", style = MaterialTheme.typography.titleMedium)
                Text("支持 TXT、HTML、EPUB、无 DRM 经典 MOBI、PDF 与图片；PDF 优先提取文本，扫描件使用本机 OCR，识别后可校正。发音使用系统 TTS，请在系统中安装对应语言的语音。")
                Text("隐私与备份", style = MaterialTheme.typography.titleMedium)
                Text("无需账号，无广告，不上传学习资料或诊断日志；应用不申请联网权限，并关闭系统自动备份。系统 TTS 服务由设备提供，其联网与隐私行为受该服务的设置控制。")
                Text("卸载会移除本地数据。请定期导出完整备份并妥善保管；备份未经加密，包含词库、文章、学习记录与设置。恢复前请先备份当前数据。旧 Alpha 可先导出备份，再在本应用恢复。")
                Text("开源与反馈", style = MaterialTheme.typography.titleMedium)
                Text("https://github.com/FangShiX/lexi-android\n问题反馈请附应用版本、系统版本和复现步骤，不要附私人文章或完整备份。")
                Text("第三方许可", style = MaterialTheme.typography.titleMedium)
                Text(licenses, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}
