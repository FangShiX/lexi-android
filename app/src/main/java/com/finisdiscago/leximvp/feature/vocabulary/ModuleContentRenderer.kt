package com.finisdiscago.leximvp.feature.vocabulary

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.finisdiscago.leximvp.domain.model.ContentModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.finisdiscago.leximvp.core.text.ModulePresentation
import com.finisdiscago.leximvp.core.text.ModulePresentationParser

@Composable
fun ModuleContentRenderer(module: ContentModule) {
    val value = remember(module.type, module.payload) {
        ModulePresentationParser.parse(module.type, module.payload)
    }
    when (value) {
        is ModulePresentation.Text -> Text(value.value, style = MaterialTheme.typography.bodyLarge)
        is ModulePresentation.Items -> value.values.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("•", color = MaterialTheme.colorScheme.primary)
                Text(item, modifier = Modifier.weight(1f))
            }
        }
        is ModulePresentation.Table -> ModuleTable(value)
        is ModulePresentation.Examples -> value.values.forEach { example ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(example.english)
                example.translation.takeIf(String::isNotBlank)?.let {
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        is ModulePresentation.Relations -> value.values.forEach { relation ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(relation.target, fontWeight = FontWeight.SemiBold)
                relation.type.takeIf(String::isNotBlank)?.let { AssistChip({}, { Text(it) }) }
            }
            relation.note.takeIf(String::isNotBlank)?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        is ModulePresentation.Media -> LocalMedia(value)
        is ModulePresentation.Fallback -> Text(value.value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModuleTable(value: ModulePresentation.Table) {
    val headers = value.headers
    Column(Modifier.horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (headers.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            headers.forEach { Text(it, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 90.dp)) }
        }
        value.rows.forEach { cells ->
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                cells.forEach { Text(it, modifier = Modifier.widthIn(min = 90.dp)) }
            }
        }
    }
}

@Composable
private fun LocalMedia(value: ModulePresentation.Media) {
    val uri = value.uri
    val caption = value.caption
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }
    LaunchedEffect(uri) {
        if (uri.isBlank()) return@LaunchedEffect
        image = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use(BitmapFactory::decodeStream)
            }.onFailure { failed = true }.getOrNull()
        }
        if (image == null) failed = true
    }
    image?.let { Image(it.asImageBitmap(), caption.ifBlank { "本地媒体" }, Modifier.fillMaxWidth().heightIn(max = 320.dp)) }
    if (failed) Text("本地媒体暂时无法读取：$uri", color = MaterialTheme.colorScheme.error)
    if (caption.isNotBlank()) Text(caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
