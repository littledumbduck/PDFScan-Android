package io.github.littledumbduck.pdfscan.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.littledumbduck.pdfscan.PdfScanApp
import io.github.littledumbduck.pdfscan.data.AppSettings
import io.github.littledumbduck.pdfscan.data.ScanDocument
import io.github.littledumbduck.pdfscan.util.PageFilter
import io.github.littledumbduck.pdfscan.util.ShareUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer

class AppViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    private val container = app as PdfScanApp
    private val repo = container.repository
    private val settingsStore = container.settings

    val settings: StateFlow<AppSettings> = settingsStore.settings

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Documentos filtrados por nombre o por contenido (texto OCR), sin distinguir tildes ni mayúsculas. */
    val documents: StateFlow<List<ScanDocument>> = combine(repo.documents, _query) { docs, q ->
        val nq = normalize(q.trim())
        if (nq.isEmpty()) docs
        else docs.filter { normalize(it.name).contains(nq) || normalize(it.fullText).contains(nq) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allDocuments: StateFlow<List<ScanDocument>> = repo.documents
    val loaded: StateFlow<Boolean> = repo.loaded

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _ocrRunning = MutableStateFlow<Set<String>>(emptySet())
    val ocrRunning: StateFlow<Set<String>> = _ocrRunning.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Documento recién creado que la UI debe abrir. */
    private val _openDocument = Channel<String>(Channel.BUFFERED)
    val openDocument = _openDocument.receiveAsFlow()

    /**
     * Si no es null, el próximo escaneo añade páginas a este documento en lugar de crear uno nuevo.
     * Se guarda en SavedStateHandle: Android puede cerrar la app mientras la cámara está abierta.
     */
    var scanTarget: String?
        get() = saved["scanTarget"]
        set(value) { saved["scanTarget"] = value }

    /** Exportación pendiente mientras el usuario elige dónde guardar (selector del sistema). */
    private var exportPassword: String? = null // solo en memoria, nunca en el estado guardado
    var pendingExport: PendingExport?
        get() = saved.get<String>("exportDoc")?.let { PendingExport(it, exportPassword) }
        set(value) {
            saved["exportDoc"] = value?.docId
            saved["exportHasPassword"] = value?.password != null
            exportPassword = value?.password
        }

    data class PendingExport(val docId: String, val password: String?)

    private val ocrMutex = Mutex()

    /** Documentos con OCR en cola o en curso (evita encolar el mismo dos veces). */
    private val ocrQueued = mutableSetOf<String>()

    init {
        viewModelScope.launch { repo.load() }
        // OCR automático para cualquier documento al que le falte texto, venga de donde venga
        // (escáner, importación, "Añadir a Escaneos" desde las herramientas PDF, páginas rotadas…).
        viewModelScope.launch {
            repo.documents.collect { docs ->
                if (!settings.value.autoOcr) return@collect
                docs.filter { d -> d.pages.any { it.ocr == null } && d.id !in ocrQueued }
                    .forEach { runOcr(it.id, notify = false) }
            }
        }
    }

    fun setQuery(q: String) = _query.update { q }

    fun repoPageFile(doc: ScanDocument, fileName: String): File = repo.pageFile(doc.id, fileName)

    // ------------------------------------------------------------------ escanear / importar

    fun onScanned(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val target = scanTarget
        scanTarget = null
        work("Guardando páginas…") {
            val doc = if (target != null && repo.get(target) != null) repo.addPages(target, uris)
            else repo.createFromUris(uris).also { _openDocument.send(it.id) }
            autoOcr(doc.id)
        }
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        work("Importando imágenes…") {
            val doc = repo.createFromUris(uris, ScanDocumentNames.imported())
            _openDocument.send(doc.id)
            autoOcr(doc.id)
        }
    }

    fun importPdf(uri: Uri) {
        work("Importando PDF…") {
            val doc = repo.importPdf(uri)
            _openDocument.send(doc.id)
            autoOcr(doc.id)
        }
    }

    // ------------------------------------------------------------------ documentos

    fun rename(id: String, name: String) = work { repo.rename(id, name) }

    fun delete(ids: Collection<String>) = work {
        repo.delete(ids)
        _messages.send(if (ids.size == 1) "Documento eliminado" else "${ids.size} documentos eliminados")
    }

    fun merge(ids: List<String>) = work("Uniendo documentos…") {
        val names = ids.mapNotNull { repo.get(it)?.name }
        val doc = repo.merge(ids, "Unión de ${names.size} documentos")
        _messages.send("Documento unido creado")
        _openDocument.send(doc.id)
    }

    fun extract(id: String, files: Set<String>) = work("Extrayendo páginas…") {
        val src = repo.get(id) ?: return@work
        val doc = repo.extract(id, files, "${src.name} (extracto)")
        _messages.send("${files.size} página(s) extraídas a un documento nuevo")
        _openDocument.send(doc.id)
    }

    /** Propone un nombre a partir de la primera línea con sentido del texto OCR. */
    fun suggestedName(doc: ScanDocument): String? =
        doc.pages.firstNotNullOfOrNull { p ->
            p.ocr?.lines?.map { it.text.trim() }?.firstOrNull { it.length in 4..60 && it.any(Char::isLetter) }
        }

    // ------------------------------------------------------------------ páginas

    fun deletePages(id: String, files: Set<String>) = work {
        val doc = repo.deletePages(id, files)
        if (doc.pages.isEmpty()) {
            repo.delete(listOf(id))
            _messages.send("Documento vacío eliminado")
        }
    }

    fun movePage(id: String, file: String, delta: Int) = work { repo.movePage(id, file, delta) }

    fun rotatePage(id: String, file: String, degrees: Int = 90) = work {
        repo.rotatePage(id, file, degrees)
        autoOcr(id)
    }

    fun applyFilter(id: String, file: String, filter: PageFilter) = work("Aplicando filtro…") {
        repo.applyFilter(id, file, filter)
    }

    fun restoreOriginal(id: String, file: String) = work { repo.restoreOriginal(id, file) }

    // ------------------------------------------------------------------ OCR

    private fun autoOcr(id: String) {
        if (settings.value.autoOcr) runOcr(id, notify = false)
    }

    fun runOcr(id: String, notify: Boolean = true) {
        if (!ocrQueued.add(id)) return
        viewModelScope.launch {
            ocrMutex.withLock {
                _ocrRunning.update { it + id }
                try {
                    // Si una página cambia durante el análisis (rotar/filtro) su resultado se descarta
                    // y se vuelve a analizar en la siguiente vuelta.
                    repeat(3) {
                        val doc = repo.get(id) ?: return@withLock
                        val pending = doc.pages.filter { it.ocr == null }
                        if (pending.isEmpty()) return@repeat
                        for (page in pending) {
                            val file = repo.pageFile(doc, page)
                            val version = file.lastModified()
                            val result = container.ocr.recognize(file)
                            repo.setOcr(id, page.file, result, version)
                        }
                    }
                    if (notify) _messages.send("Texto reconocido")
                } catch (e: Exception) {
                    if (notify) _messages.send("Error de OCR: ${e.message ?: e.javaClass.simpleName}")
                } finally {
                    _ocrRunning.update { it - id }
                    ocrQueued.remove(id)
                }
            }
        }
    }

    // ------------------------------------------------------------------ compartir / exportar

    private suspend fun buildPdf(doc: ScanDocument, password: String?): File {
        val ctx = getApplication<Application>()
        val out = File(ShareUtils.shareDir(ctx), "${ShareUtils.safeFileName(doc.name)}.pdf")
        container.pdfExporter.export(doc, { repo.pageFile(doc.id, it) }, settings.value, out, password)
        return out
    }

    fun sharePdf(id: String, password: String? = null) = work("Generando PDF…") {
        val ctx = getApplication<Application>()
        val doc = repo.get(id) ?: return@work
        ShareUtils.clearShareDir(ctx)
        val pdf = buildPdf(doc, password)
        ShareUtils.shareFiles(ctx, listOf(pdf), "application/pdf", doc.name)
    }

    fun shareImages(id: String) = work("Preparando imágenes…") {
        val ctx = getApplication<Application>()
        val doc = repo.get(id) ?: return@work
        ShareUtils.clearShareDir(ctx)
        val base = ShareUtils.safeFileName(doc.name)
        val files = withContext(Dispatchers.IO) {
            doc.pages.mapIndexed { i, p ->
                val f = File(ShareUtils.shareDir(ctx), if (doc.pages.size == 1) "$base.jpg" else "${base}_${i + 1}.jpg")
                repo.pageFile(doc, p).copyTo(f, overwrite = true)
            }
        }
        ShareUtils.shareFiles(ctx, files, "image/jpeg", doc.name)
    }

    fun shareText(id: String) {
        val doc = repo.get(id) ?: return
        val text = doc.fullText
        if (text.isBlank()) {
            _messages.trySend("No hay texto reconocido todavía")
            return
        }
        ShareUtils.shareText(getApplication(), text, doc.name)
    }

    /** Escribe el PDF en la ubicación elegida por el usuario con el selector del sistema. */
    fun writeExport(target: Uri) {
        val pending = pendingExport ?: return
        val lostPassword = saved.get<Boolean>("exportHasPassword") == true && pending.password == null
        pendingExport = null
        if (lostPassword) {
            _messages.trySend("Se perdió la contraseña al cerrarse la app. Vuelve a intentarlo.")
            return
        }
        work("Guardando PDF…") {
            val ctx = getApplication<Application>()
            val doc = repo.get(pending.docId) ?: return@work
            val pdf = buildPdf(doc, pending.password)
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(target)?.use { out -> pdf.inputStream().use { it.copyTo(out) } }
                    ?: error("No se pudo escribir el archivo")
            }
            pdf.delete()
            _messages.send("PDF guardado")
        }
    }

    // ------------------------------------------------------------------ ajustes

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    // ------------------------------------------------------------------ util

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

    private fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}

private object ScanDocumentNames {
    fun imported() = io.github.littledumbduck.pdfscan.data.DocumentRepository.defaultName("Imágenes")
}
