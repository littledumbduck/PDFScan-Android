package io.github.littledumbduck.pdfscan.tools

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.littledumbduck.pdfscan.PdfScanApp
import io.github.littledumbduck.pdfscan.util.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Un PDF preparado para usar en las herramientas (copia local, sin cifrado). */
data class PdfFile(val file: File, val name: String, val pages: Int)

data class ToolResult(
    val title: String,
    val files: List<File>,
    val details: String? = null,
    val images: Boolean = false,
    val text: String? = null,
)

/** Para qué se está eligiendo un PDF. */
enum class InputTarget { SINGLE, MERGE }

data class PasswordRequest(
    val file: File,
    val name: String,
    val target: InputTarget,
    val wrong: Boolean = false,
    val openViewer: Boolean = false,
)

enum class SplitMode(val label: String) {
    EACH_PAGE("Cada página en un PDF"),
    RANGES("Un PDF por cada rango"),
    EXTRACT("Extraer los rangos en un solo PDF"),
}

class ToolsViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as PdfScanApp
    private val workDir = File(app.filesDir, "pdf_work").apply { mkdirs() }
    private val outDir = File(app.filesDir, "pdf_output").apply { mkdirs() }

    val signatures = container.signatures

    private val _input = MutableStateFlow<PdfFile?>(null)
    val input: StateFlow<PdfFile?> = _input.asStateFlow()

    private val _mergeList = MutableStateFlow<List<PdfFile>>(emptyList())
    val mergeList: StateFlow<List<PdfFile>> = _mergeList.asStateFlow()

    private val _outputs = MutableStateFlow(listOutputs())
    val outputs: StateFlow<List<File>> = _outputs.asStateFlow()

    private val _result = MutableStateFlow<ToolResult?>(null)
    val result: StateFlow<ToolResult?> = _result.asStateFlow()

    private val _passwordRequest = MutableStateFlow<PasswordRequest?>(null)
    val passwordRequest: StateFlow<PasswordRequest?> = _passwordRequest.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Rutas a las que la UI debe navegar (p. ej. "result"). */
    private val _navigate = Channel<String>(Channel.BUFFERED)
    val navigate = _navigate.receiveAsFlow()

    /** Archivo pendiente de "Guardar en…" mientras el usuario elige destino. */
    var pendingSave: File? = null

    init {
        // Limpia copias de trabajo de más de un día.
        val limit = System.currentTimeMillis() - 24L * 3600 * 1000
        workDir.listFiles()?.filter { it.lastModified() < limit }?.forEach { it.deleteRecursively() }
    }

    // ------------------------------------------------------------------ entradas

    fun clearInput() { _input.value = null }

    fun pickUri(uri: Uri, target: InputTarget) = work("Abriendo PDF…") {
        val ctx = getApplication<Application>()
        val name = displayName(uri)
        val raw = File(workDir, "raw_${UUID.randomUUID()}.pdf")
        withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { input.copyTo(it) } }
                ?: error("No se pudo leer el archivo")
        }
        prepare(raw, name, target, password = null)
    }

    fun submitPassword(password: String) {
        val req = _passwordRequest.value ?: return
        _passwordRequest.value = null
        work("Abriendo PDF…") { prepare(req.file, req.name, req.target, password, req.openViewer) }
    }

    fun cancelPassword() {
        _passwordRequest.value?.file?.delete()
        _passwordRequest.value = null
    }

    private suspend fun prepare(raw: File, name: String, target: InputTarget, password: String?, openViewer: Boolean = false) {
        val clean = workFile(name)
        val pages = try {
            PdfTools.normalize(raw, clean, password)
        } catch (_: PdfPasswordException) {
            _passwordRequest.value = PasswordRequest(raw, name, target, wrong = password != null, openViewer = openViewer)
            return
        }
        raw.delete()
        addInput(PdfFile(clean, name, pages), target)
        if (openViewer) _navigate.send("viewer/${Uri.encode(clean.absolutePath)}")
    }

    /** Usa un documento escaneado de la biblioteca como entrada. */
    fun pickScans(docIds: List<String>, target: InputTarget) = work("Preparando documento…") {
        for (docId in docIds) {
            val doc = container.repository.get(docId) ?: continue
            val out = workFile("${doc.name}.pdf")
            container.pdfExporter.export(doc, { container.repository.pageFile(doc.id, it) }, container.settings.settings.value, out)
            addInput(PdfFile(out, out.name, doc.pages.size), target)
        }
    }

    /** Abre un escaneo en el visor de PDF (para firmarlo, comprimirlo, etc.). */
    fun openScanInViewer(docId: String) = work("Preparando documento…") {
        val doc = container.repository.get(docId) ?: return@work
        val out = workFile("${doc.name}.pdf")
        container.pdfExporter.export(doc, { container.repository.pageFile(doc.id, it) }, container.settings.settings.value, out)
        _navigate.send("viewer/${Uri.encode(out.absolutePath)}")
    }

    /** Usa un archivo ya generado (resultado o visor) como entrada. */
    fun useFile(file: File, target: InputTarget = InputTarget.SINGLE) = work {
        addInput(PdfFile(file, file.name, PdfTools.pageCount(file)), target)
    }

    private fun addInput(pdf: PdfFile, target: InputTarget) {
        when (target) {
            InputTarget.SINGLE -> _input.value = pdf
            InputTarget.MERGE -> _mergeList.update { it + pdf }
        }
    }

    fun removeFromMerge(index: Int) = _mergeList.update { it.filterIndexed { i, _ -> i != index } }

    fun moveInMerge(index: Int, delta: Int) = _mergeList.update { list ->
        val to = (index + delta).coerceIn(0, list.lastIndex)
        list.toMutableList().apply { add(to, removeAt(index)) }
    }

    fun clearMerge() { _mergeList.value = emptyList() }

    // ------------------------------------------------------------------ herramientas

    fun merge() = work("Uniendo PDFs…") {
        val list = _mergeList.value
        if (list.size < 2) { _messages.send("Añade al menos 2 PDFs"); return@work }
        val out = newOutput(list.first().name, "unido")
        PdfTools.merge(list.map { it.file }, out)
        finish(ToolResult("PDF unido", listOf(out), "${list.size} archivos · ${list.sumOf { it.pages }} páginas"))
        _mergeList.value = emptyList()
    }

    fun split(mode: SplitMode, rangesText: String) = work("Dividiendo…") {
        val pdf = _input.value ?: return@work
        val ranges = when (mode) {
            SplitMode.EACH_PAGE -> (0 until pdf.pages).map { listOf(it) }
            SplitMode.RANGES -> parseRanges(rangesText, pdf.pages)
            SplitMode.EXTRACT -> listOf(parseRanges(rangesText, pdf.pages).flatten())
        }
        if (ranges.isEmpty() || ranges.any { it.isEmpty() }) {
            _messages.send("Rangos no válidos. Ejemplo: 1-3, 5, 8-10"); return@work
        }
        val files = PdfTools.split(pdf.file, ranges) { _, pages ->
            newOutput(pdf.name, "pág ${describe(pages)}")
        }
        finish(ToolResult("PDF dividido", files, "${files.size} archivo(s)"))
    }

    fun organize(ops: List<PageOp>) = work("Guardando…") {
        val pdf = _input.value ?: return@work
        if (ops.isEmpty()) { _messages.send("No quedan páginas"); return@work }
        val out = newOutput(pdf.name, "organizado")
        PdfTools.organize(pdf.file, ops, out)
        finish(ToolResult("Páginas organizadas", listOf(out), "${ops.size} páginas"))
    }

    fun extractPages(pages: List<Int>) = work("Extrayendo…") {
        val pdf = _input.value ?: return@work
        val out = newOutput(pdf.name, "pág ${describe(pages)}")
        PdfTools.split(pdf.file, listOf(pages)) { _, _ -> out }
        finish(ToolResult("Páginas extraídas", listOf(out), "${pages.size} páginas"))
    }

    fun sign(stamps: List<Stamp>) = work("Firmando…") {
        val pdf = _input.value ?: return@work
        if (stamps.isEmpty()) { _messages.send("Añade una firma o un texto primero"); return@work }
        val out = newOutput(pdf.name, "firmado")
        PdfTools.stamp(pdf.file, stamps, out)
        finish(ToolResult("PDF firmado", listOf(out)))
    }

    fun compress(level: CompressLevel) = work("Comprimiendo…") {
        val pdf = _input.value ?: return@work
        val out = newOutput(pdf.name, "comprimido")
        val (before, after) = PdfTools.compress(pdf.file, level, out)
        val saved = if (before > 0) ((before - after) * 100 / before) else 0
        val details = "${size(before)} → ${size(after)}" +
            if (after < before) "  (−$saved %)" else "  (no se ha podido reducir más)"
        finish(ToolResult("PDF comprimido", listOf(out), details))
    }

    fun protect(password: String) = work("Cifrando…") {
        val pdf = _input.value ?: return@work
        val out = newOutput(pdf.name, "protegido")
        PdfTools.protect(pdf.file, password, out)
        finish(ToolResult("PDF protegido", listOf(out), "Cifrado AES-256. Guarda bien la contraseña."))
    }

    /** La entrada ya se abrió con la contraseña; basta con guardarla sin cifrado. */
    fun unlock() = work("Quitando contraseña…") {
        val pdf = _input.value ?: return@work
        val out = newOutput(pdf.name, "sin contraseña")
        withContext(Dispatchers.IO) { pdf.file.copyTo(out, overwrite = true) }
        finish(ToolResult("Contraseña eliminada", listOf(out)))
    }

    fun pageNumbers(position: NumberPosition, format: NumberFormat) = work("Numerando páginas…") {
        val pdf = _input.value ?: return@work
        val out = newOutput(pdf.name, "numerado")
        PdfTools.pageNumbers(pdf.file, position, format, out)
        finish(ToolResult("Páginas numeradas", listOf(out)))
    }

    fun watermark(text: String, opacity: Float) = work("Añadiendo marca de agua…") {
        val pdf = _input.value ?: return@work
        val out = newOutput(pdf.name, "marca de agua")
        PdfTools.watermark(pdf.file, text, opacity, out)
        finish(ToolResult("Marca de agua añadida", listOf(out)))
    }

    fun toImages(dpi: Int) = work("Convirtiendo a imágenes…") {
        val pdf = _input.value ?: return@work
        val base = ShareUtils.safeFileName(pdf.name.removeSuffix(".pdf"))
        val dir = File(outDir, "img_${UUID.randomUUID().toString().take(8)}").apply { mkdirs() }
        val files = PdfPreview.toImages(pdf.file, dpi) { i -> File(dir, "${base}_${i + 1}.jpg") }
        finish(ToolResult("PDF convertido a imágenes", files, "${files.size} imágenes JPG", images = true), refresh = false)
    }

    fun extractText() = work("Extrayendo texto…") {
        val pdf = _input.value ?: return@work
        val text = PdfTools.extractText(pdf.file).trim()
        finish(
            ToolResult(
                "Texto extraído",
                emptyList(),
                if (text.isEmpty()) "Este PDF no tiene capa de texto. Impórtalo a Escaneos para reconocer el texto con OCR." else null,
                text = text,
            ),
            refresh = false,
        )
    }

    // ------------------------------------------------------------------ resultados / archivos

    fun shareFiles(files: List<File>) {
        val images = files.all { it.extension.equals("jpg", true) }
        ShareUtils.shareFiles(getApplication(), files, if (images) "image/jpeg" else "application/pdf", files.first().nameWithoutExtension)
    }

    fun shareText(text: String) = ShareUtils.shareText(getApplication(), text, "Texto extraído")

    fun writeSave(target: Uri) {
        val file = pendingSave ?: return
        pendingSave = null
        work("Guardando…") {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(target)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: error("No se pudo escribir el archivo")
            }
            _messages.send("Archivo guardado")
        }
    }

    /** Importa un PDF o imágenes a la biblioteca de escaneos (para OCR, editar páginas, etc.). */
    fun addToScans(files: List<File>) = work("Añadiendo a Escaneos…") {
        val repo = container.repository
        if (files.all { it.extension.equals("jpg", true) }) {
            repo.createFromUris(files.map { Uri.fromFile(it) })
        } else {
            files.forEach { repo.importPdf(Uri.fromFile(it), it.nameWithoutExtension) }
        }
        _messages.send("Añadido a Escaneos")
    }

    fun deleteOutput(file: File) = work {
        PdfPreview.close()
        withContext(Dispatchers.IO) { file.delete() }
        _outputs.value = listOutputs()
    }

    fun renameOutput(file: File, newName: String) = work {
        val target = File(outDir, ShareUtils.safeFileName(newName.removeSuffix(".pdf")) + ".pdf")
        if (target.exists()) { _messages.send("Ya existe un archivo con ese nombre"); return@work }
        PdfPreview.close()
        withContext(Dispatchers.IO) { file.renameTo(target) }
        _outputs.value = listOutputs()
    }

    /** Abre un PDF recibido con "Abrir con" (ACTION_VIEW) en el visor. */
    fun openExternal(uri: Uri) = work("Abriendo PDF…") {
        val ctx = getApplication<Application>()
        val name = displayName(uri)
        val raw = File(workDir, "raw_${UUID.randomUUID()}.pdf")
        withContext(Dispatchers.IO) {
            ctx.contentResolver.openInputStream(uri)?.use { input -> raw.outputStream().use { input.copyTo(it) } }
                ?: error("No se pudo leer el archivo")
        }
        val clean = workFile(name)
        try {
            PdfTools.normalize(raw, clean, null)
            raw.delete()
            _navigate.send("viewer/${Uri.encode(clean.absolutePath)}")
        } catch (_: PdfPasswordException) {
            clean.parentFile?.delete()
            _passwordRequest.value = PasswordRequest(raw, name, InputTarget.SINGLE, openViewer = true)
        }
    }

    // ------------------------------------------------------------------ util

    private suspend fun finish(result: ToolResult, refresh: Boolean = true) {
        if (refresh) _outputs.value = listOutputs()
        _result.value = result
        _navigate.send("result")
    }

    /** Copia de trabajo con el nombre real del archivo (en su propia carpeta para no chocar). */
    private fun workFile(name: String): File {
        val dir = File(workDir, "w_${UUID.randomUUID().toString().take(8)}").apply { mkdirs() }
        val safe = ShareUtils.safeFileName(name.removeSuffix(".pdf").removeSuffix(".PDF"))
        return File(dir, "$safe.pdf")
    }

    private fun listOutputs() =
        outDir.listFiles { f -> f.isFile && f.extension.equals("pdf", true) }.orEmpty().sortedByDescending { it.lastModified() }

    private fun newOutput(sourceName: String, suffix: String): File {
        val base = ShareUtils.safeFileName("${sourceName.removeSuffix(".pdf").removeSuffix(".PDF")} ($suffix)").take(100)
        var f = File(outDir, "$base.pdf")
        var n = 2
        while (f.exists()) f = File(outDir, "$base $n.pdf").also { n++ }
        return f
    }

    private fun displayName(uri: Uri): String {
        val ctx = getApplication<Application>()
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "documento.pdf"
        return if (name.endsWith(".pdf", true)) name else "$name.pdf"
    }

    private fun work(label: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (label != null) _busy.value = label
            try {
                block()
            } catch (e: Exception) {
                _messages.send("Error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                if (label != null) _busy.value = null
            }
        }
    }

    companion object {
        /** "1-3, 5, 8-10" → [[0,1,2],[4],[7,8,9]] (índices 0-based). Devuelve lista vacía si no es válido. */
        fun parseRanges(text: String, pageCount: Int): List<List<Int>> {
            val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return emptyList()
            return parts.map { part ->
                val bounds = part.split('-').map { it.trim() }
                val (a, b) = when (bounds.size) {
                    1 -> bounds[0].toIntOrNull() to bounds[0].toIntOrNull()
                    2 -> bounds[0].toIntOrNull() to (bounds[1].toIntOrNull() ?: pageCount.takeIf { bounds[1].isEmpty() })
                    else -> null to null
                }
                if (a == null || b == null || a < 1 || b > pageCount || a > b) return emptyList()
                (a - 1 until b).toList()
            }
        }

        fun describe(pages: List<Int>): String =
            if (pages.size == 1) "${pages[0] + 1}"
            else if (pages == (pages.first()..pages.last()).toList()) "${pages.first() + 1}-${pages.last() + 1}"
            else "${pages.size} págs"

        fun size(bytes: Long): String = when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
            else -> "${(bytes / 1024).coerceAtLeast(1)} KB"
        }
    }
}
