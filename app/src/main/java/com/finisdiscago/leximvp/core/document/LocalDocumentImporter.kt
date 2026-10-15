package com.finisdiscago.leximvp.core.document

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.text.Html
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URLDecoder
import java.nio.charset.Charset
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

data class ImportedChapter(
    val title: String,
    val content: String,
    val locator: String,
    val images: List<ImportedImage> = emptyList()
)
data class ImportedImage(val localPath: String, val alt: String = "")
data class ImportedDocument(
    val title: String,
    val format: String,
    val chapters: List<ImportedChapter>,
    val warnings: List<String> = emptyList(),
    val assetPath: String? = null,
    val originalFileName: String? = null,
    val mimeType: String? = null,
    val images: List<ImportedImage> = emptyList()
) {
    val text: String get() = chapters.joinToString("\n\n") { it.title + "\n" + it.content }
}

class DocumentImportException(message: String) : IllegalArgumentException(message)

class LocalDocumentImporter(private val context: Context) {
    private val resolver: ContentResolver = context.contentResolver
    suspend fun import(uri: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }): ImportedDocument = withContext(Dispatchers.IO) {
        val title = displayName(uri).substringBeforeLast('.').ifBlank { "导入文档" }
        val type = resolver.getType(uri).orEmpty().lowercase()
        val extension = displayName(uri).substringAfterLast('.', "").lowercase()
        val imported = when {
            extension == "epub" || type == "application/epub+zip" -> importEpub(uri, title, onProgress)
            extension == "pdf" || type == "application/pdf" -> importPdf(uri, title, onProgress)
            extension in setOf("mobi", "azw") -> importMobi(uri, title)
            extension in setOf("png", "jpg", "jpeg", "webp") || type.startsWith("image/") -> importImage(uri, title)
            extension in setOf("txt", "md", "html", "htm") || type.startsWith("text/") -> importText(uri, title, extension)
            extension == "azw3" -> throw DocumentImportException("AZW3/KF8 尚不支持；请转换为无 DRM EPUB，原文件未修改。")
            else -> throw DocumentImportException("不支持此文档格式")
        }
        val name = displayName(uri)
        imported.copy(
            assetPath = preserveOriginal(uri, name),
            originalFileName = name,
            mimeType = type.ifBlank { null }
        )
    }

    private fun preserveOriginal(uri: Uri, originalName: String): String {
        val extension = originalName.substringAfterLast('.', "").lowercase().take(10)
        val relative = "imports/${UUID.randomUUID()}${extension.takeIf(String::isNotBlank)?.let { ".$it" }.orEmpty()}"
        val target = File(context.filesDir, relative)
        target.parentFile?.mkdirs()
        resolver.openInputStream(uri)?.buffered()?.use { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > 512L * 1024 * 1024) throw DocumentImportException("原始文档超过 512 MB，未保存")
                    output.write(buffer, 0, read)
                }
            }
        } ?: throw DocumentImportException("无法保存原始文档")
        return relative
    }

    private suspend fun importEpub(uri: Uri, title: String, progress: (Int, Int) -> Unit): ImportedDocument {
        val html = linkedMapOf<String, ByteArray>()
        val imageEntries = mutableMapOf<String, ByteArray>()
        val packageXml = mutableMapOf<String, ByteArray>()
        var expanded = 0L
        resolver.openInputStream(uri)?.buffered()?.use { source ->
            ZipInputStream(source).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    val extension = entry.name.substringAfterLast('.').lowercase()
                    if (!entry.isDirectory && extension in setOf("xhtml", "html", "htm", "jpg", "jpeg", "png", "gif", "webp", "xml", "opf")) {
                        val data = zip.readEntry(if (extension in setOf("xhtml", "html", "htm")) 4 * 1024 * 1024 else 32 * 1024 * 1024)
                        expanded += data.size
                        if (expanded > 128L * 1024 * 1024) throw DocumentImportException("EPUB 展开内容超过 128 MB")
                        when (extension) {
                            in setOf("xhtml", "html", "htm") -> html[entry.name] = data
                            in setOf("xml", "opf") -> packageXml[entry.name] = data
                            else -> imageEntries[entry.name] = data
                        }
                    }
                }
            }
        } ?: throw DocumentImportException("无法打开 EPUB")
        if (html.isEmpty()) throw DocumentImportException("EPUB 中没有可读章节")
        val orderedPaths = epubSpineOrder(html.keys, packageXml)
        val documentId = UUID.randomUUID().toString()
        val importedImages = mutableListOf<ImportedImage>()
        val imageTag = Regex("<(?:img|image)\\b[^>]*?(?:src|xlink:href|href)\\s*=\\s*['\"]([^'\"]+)['\"][^>]*?/?>", RegexOption.IGNORE_CASE)
        val altAttribute = Regex("\\balt\\s*=\\s*['\"]([^'\"]*)['\"]", RegexOption.IGNORE_CASE)
        val titleTag = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val chapters = orderedPaths.mapIndexed { index, path ->
            val bytes = html.getValue(path)
            progress(index + 1, orderedPaths.size)
            val raw = bytes.toString(Charsets.UTF_8)
            val chapterImages = mutableListOf<ImportedImage>()
            val withImageMarkers = imageTag.replace(raw) { match ->
                val source = resolveZipPath(path, match.groupValues[1])
                val image = imageEntries[source]
                if (image != null) {
                    val extension = source.substringAfterLast('.', "jpg").lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "gif", "webp") } ?: "jpg"
                    val relative = "media/$documentId/${importedImages.size}.$extension"
                    val target = File(context.filesDir, relative)
                    target.parentFile?.mkdirs(); target.writeBytes(image)
                    val importedImage = ImportedImage(relative, altAttribute.find(match.value)?.groupValues?.get(1).orEmpty())
                    importedImages += importedImage
                    chapterImages += importedImage
                    "\n\uFFFC\n"
                } else ""
            }
            val plain = Html.fromHtml(withImageMarkers, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\n{3,}"), "\n\n").trim()
            val chapterTitle = titleTag.find(raw)?.groupValues?.get(1)
                ?.let { Html.fromHtml(it, Html.FROM_HTML_MODE_LEGACY).toString().trim() }
                ?.takeIf(String::isNotBlank)
                ?: path.substringAfterLast('/').substringBeforeLast('.')
            ImportedChapter(chapterTitle, plain, "epub:$path", chapterImages)
        }.filter { it.content.isNotBlank() }
        return ImportedDocument(title, "EPUB", chapters, images = importedImages)
    }

    private fun epubSpineOrder(paths: Set<String>, packageXml: Map<String, ByteArray>): List<String> {
        val container = packageXml.entries.firstOrNull { it.key.endsWith("META-INF/container.xml", true) }
            ?.value?.toString(Charsets.UTF_8).orEmpty()
        val rootPath = Regex("<rootfile\\b[^>]*full-path\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .find(container)?.groupValues?.get(1)
        val opfPath = rootPath?.takeIf(packageXml::containsKey)
            ?: packageXml.keys.firstOrNull { it.endsWith(".opf", true) }
            ?: return paths.toList()
        val opf = packageXml.getValue(opfPath).toString(Charsets.UTF_8)
        val itemTags = Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf)
        val attributes = Regex("([\\w:-]+)\\s*=\\s*['\"]([^'\"]*)['\"]")
        val manifest = itemTags.mapNotNull { match ->
            val values = attributes.findAll(match.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val id = values["id"] ?: return@mapNotNull null
            val href = values["href"] ?: return@mapNotNull null
            id to resolveZipPath(opfPath, href)
        }.toMap()
        val spineIds = Regex("<itemref\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).mapNotNull { match ->
            attributes.findAll(match.value).firstOrNull { it.groupValues[1].equals("idref", true) }?.groupValues?.get(2)
        }
        val ordered = spineIds.mapNotNull(manifest::get).filter(paths::contains).distinct().toMutableList()
        ordered += paths.filterNot(ordered::contains)
        return ordered
    }

    private fun resolveZipPath(documentPath: String, source: String): String {
        val base = documentPath.substringBeforeLast('/', "")
        val cleanSource = URLDecoder.decode(source.substringBefore('#').substringBefore('?'), Charsets.UTF_8.name())
        val parts = (if (base.isBlank()) cleanSource else "$base/$cleanSource").replace('\\', '/').split('/')
        val resolved = mutableListOf<String>()
        parts.forEach { part -> when (part) { "", "." -> Unit; ".." -> if (resolved.isNotEmpty()) resolved.removeAt(resolved.lastIndex); else -> resolved += part } }
        return resolved.joinToString("/")
    }

    private suspend fun importPdf(uri: Uri, title: String, progress: (Int, Int) -> Unit): ImportedDocument {
        PDFBoxResourceLoader.init(context)
        val textPages = runCatching {
            resolver.openInputStream(uri)?.buffered()?.use { input ->
                PDDocument.load(input).use { document ->
                    if (document.numberOfPages > 2_000) throw DocumentImportException("PDF 页数超过 2000 页")
                    val stripper = PDFTextStripper().apply { sortByPosition = true }
                    val pages = mutableListOf<String>()
                    for (index in 0 until document.numberOfPages) {
                        currentCoroutineContext().ensureActive()
                        stripper.startPage = index + 1
                        stripper.endPage = index + 1
                        pages += stripper.getText(document).replace(Regex("[ \\t]+\\n"), "\n").replace(Regex("\\n{3,}"), "\n\n").trim()
                    }
                    pages
                }
            } ?: throw DocumentImportException("无法打开 PDF")
        }.getOrNull()
        val fd = resolver.openFileDescriptor(uri, "r") ?: throw DocumentImportException("无法打开 PDF")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            PdfRenderer(fd).use { renderer ->
                if (renderer.pageCount > 2_000) throw DocumentImportException("PDF 页数超过 2000 页")
                val chapters = mutableListOf<ImportedChapter>()
                var ocrPages = 0
                for (index in 0 until renderer.pageCount) {
                    currentCoroutineContext().ensureActive()
                    var text = textPages?.getOrNull(index).orEmpty()
                    if (text.isBlank()) renderer.openPage(index).use { page ->
                            val scale = (1600f / page.width).coerceIn(1f, 3f)
                            val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()
                            bitmap.recycle()
                            ocrPages++
                    }
                    chapters += ImportedChapter("第 ${index + 1} 页", text, "pdf:page=$index")
                    progress(index + 1, renderer.pageCount)
                }
                val format = when {
                    ocrPages == 0 -> "PDF-TEXT"
                    ocrPages == renderer.pageCount -> "PDF-OCR"
                    else -> "PDF-MIXED"
                }
                val warnings = when {
                    ocrPages == 0 -> emptyList()
                    ocrPages == renderer.pageCount -> listOf("未检测到可用文本层，全部页面由本地 OCR 提取；请校正识别错误。")
                    else -> listOf("优先保留 PDF 文本层；其中 $ocrPages 页没有可用文字，已改用本地 OCR。")
                }
                return ImportedDocument(title, format, chapters, warnings)
            }
        } finally { recognizer.close(); fd.close() }
    }

    private fun importMobi(uri: Uri, title: String): ImportedDocument {
        val data = resolver.openInputStream(uri)?.use { it.readLimited(128 * 1024 * 1024) }
            ?: throw DocumentImportException("无法打开 MOBI")
        if (data.size < 100) throw DocumentImportException("MOBI 文件头不完整")
        val recordCount = data.u16(76)
        if (recordCount < 2 || 78 + recordCount * 8 > data.size) throw DocumentImportException("MOBI 记录表无效")
        val offsets = (0 until recordCount).map { data.u32(78 + it * 8).toInt() }
        if (offsets.any { it !in 0 until data.size } || offsets.zipWithNext().any { it.first >= it.second }) throw DocumentImportException("MOBI 记录偏移无效")
        val header = offsets.first()
        if (header + 40 > offsets[1] || data.copyOfRange(header + 16, header + 20).toString(Charsets.US_ASCII) != "MOBI") {
            throw DocumentImportException("文件不是受支持的经典 MOBI")
        }
        val compression = data.u16(header)
        if (data.u32(header + 36) >= 8) throw DocumentImportException("KF8 格式尚不支持；请转换为无 DRM EPUB")
        val textLength = data.u32(header + 4).coerceAtMost(128L * 1024 * 1024).toInt()
        val textRecordCount = data.u16(header + 8)
        val encryption = data.u16(header + 12)
        if (encryption != 0) throw DocumentImportException("该 MOBI 含 DRM/加密，无法本地导入")
        if (compression !in setOf(1, 2)) throw DocumentImportException("该 MOBI 使用 HUFF/CDIC 压缩，当前离线解析器不支持；可转换为 EPUB 后导入")
        if (textRecordCount <= 0 || textRecordCount >= recordCount) throw DocumentImportException("MOBI 正文记录无效")
        val encoding = data.u32(header + 28).toInt()
        val charset = when (encoding) { 65001 -> Charsets.UTF_8; 1252 -> Charset.forName("windows-1252"); else -> Charsets.UTF_8 }
        val output = ByteArrayOutputStream(textLength.coerceAtLeast(1024))
        for (record in 1..textRecordCount) {
            val start = offsets[record]
            val end = offsets.getOrElse(record + 1) { data.size }
            val bytes = data.copyOfRange(start, end)
            output.write(if (compression == 2) decompressPalmDoc(bytes) else bytes)
            if (output.size() > 128 * 1024 * 1024) throw DocumentImportException("MOBI 正文超过 128 MB")
        }
        val raw = output.toByteArray().copyOfRange(0, output.size().coerceAtMost(textLength)).toString(charset).trim('\u0000')
        val sections = raw.split(Regex("(?i)<mbp:pagebreak\\s*/?>")).map { html ->
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\n{3,}"), "\n\n").trim()
        }.filter(String::isNotBlank)
        if (sections.isEmpty()) throw DocumentImportException("MOBI 中没有可读正文")
        return ImportedDocument(title, "MOBI", sections.mapIndexed { index, text -> ImportedChapter("第 ${index + 1} 节", text, "mobi:section=$index") },
            listOf("已使用内置无 DRM PalmDOC/MOBI 解析器；复杂版式和嵌入媒体可能被简化。"))
    }

    private suspend fun importImage(uri: Uri, title: String): ImportedDocument {
        val image = InputImage.fromFilePath(context, uri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try { ImportedDocument(title, "IMAGE-OCR", listOf(ImportedChapter("图像", recognizer.process(image).await().text, "image:0")), listOf("文字由本地 OCR 提取。")) }
        finally { recognizer.close() }
    }

    private fun importText(uri: Uri, title: String, extension: String): ImportedDocument {
        val bytes = resolver.openInputStream(uri)?.use { it.readLimited(32 * 1024 * 1024) } ?: throw DocumentImportException("无法打开文档")
        val raw = bytes.toString(Charsets.UTF_8)
        val text = if (extension in setOf("html", "htm")) Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString() else raw
        return ImportedDocument(title, extension.uppercase().ifBlank { "TEXT" }, listOf(ImportedChapter("正文", text, "text:0")))
    }

    private fun displayName(uri: Uri): String = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment.orEmpty()
}

private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
    val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
    while (true) { val count = read(buffer); if (count < 0) break; if (output.size() + count > limit) throw DocumentImportException("单个内容超过限制"); output.write(buffer, 0, count) }
    return output.toByteArray()
}
private fun ByteArray.u16(offset: Int): Int {
    if (offset < 0 || offset + 2 > size) throw DocumentImportException("文档结构越界")
    return ((this[offset].toInt() and 0xff) shl 8) or (this[offset + 1].toInt() and 0xff)
}
private fun ByteArray.u32(offset: Int): Long {
    if (offset < 0 || offset + 4 > size) throw DocumentImportException("文档结构越界")
    return ((this[offset].toLong() and 0xff) shl 24) or ((this[offset + 1].toLong() and 0xff) shl 16) or
        ((this[offset + 2].toLong() and 0xff) shl 8) or (this[offset + 3].toLong() and 0xff)
}
private fun decompressPalmDoc(input: ByteArray): ByteArray {
    val out = ArrayList<Byte>(input.size * 2)
    var index = 0
    while (index < input.size) {
        val value = input[index++].toInt() and 0xff
        when {
            value == 0 -> out += 0.toByte()
            value in 1..8 -> repeat(value.coerceAtMost(input.size - index)) { out += input[index++] }
            value in 9..0x7f -> out += value.toByte()
            value >= 0xc0 -> { out += 0x20.toByte(); out += (value xor 0x80).toByte() }
            else -> {
                if (index >= input.size) throw DocumentImportException("PalmDOC 压缩记录损坏")
                val pair = (value shl 8) or (input[index++].toInt() and 0xff)
                val distance = (pair shr 3) and 0x7ff
                val length = (pair and 7) + 3
                if (distance == 0 || distance > out.size) throw DocumentImportException("PalmDOC 回溯距离无效")
                repeat(length) { out += out[out.size - distance] }
            }
        }
        if (out.size > 16 * 1024 * 1024) throw DocumentImportException("单个 MOBI 记录解压后过大")
    }
    return out.toByteArray()
}
private fun ZipInputStream.readEntry(limit: Int) = (this as java.io.InputStream).readLimited(limit)
private suspend fun <T> Task<T>.await(): T = suspendCoroutine { continuation -> addOnSuccessListener { continuation.resume(it) }.addOnFailureListener { continuation.resumeWithException(it) } }
