package io.github.littledumbduck.pdfscan.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import io.github.littledumbduck.pdfscan.util.ImageUtils
import io.github.littledumbduck.pdfscan.util.PageFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Almacén local de documentos. Cada documento es una carpeta normal:
 *
 * ```
 * files/documents/<id>/
 *     meta.json        nombre, fechas, orden de páginas y OCR
 *     pages/p_xxx.jpg  imágenes de las páginas
 * ```
 *
 * El PDF se genera bajo demanda al compartir/exportar, así siempre refleja los ajustes actuales.
 */
class DocumentRepository(private val context: Context) {

    private val root = File(context.filesDir, "documents").apply { mkdirs() }
    private val mutex = Mutex()

    private val _documents = MutableStateFlow<List<ScanDocument>>(emptyList())
    val documents: StateFlow<List<ScanDocument>> = _documents.asStateFlow()

    fun get(id: String): ScanDocument? = _documents.value.firstOrNull { it.id == id }

    fun pageFile(docId: String, fileName: String) = File(File(File(root, docId), "pages"), fileName)
    fun pageFile(doc: ScanDocument, page: Page) = pageFile(doc.id, page.file)

    suspend fun load() = withContext(Dispatchers.IO) {
        mutex.withLock {
            val docs = root.listFiles { f -> f.isDirectory }.orEmpty().mapNotNull { dir ->
                runCatching { scanDocumentFromJson(JSONObject(File(dir, META).readText())) }.getOrNull()
            }
            _documents.value = docs.sortedByDescending { it.updatedAt }
        }
    }

    // ------------------------------------------------------------------ creación / importación

    suspend fun createFromUris(uris: List<Uri>, name: String? = null): ScanDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = newId()
            val pages = uris.map { importImage(id, it) }
            val now = System.currentTimeMillis()
            val doc = ScanDocument(id, name ?: defaultName(), now, now, pages)
            save(doc)
            doc
        }
    }

    /** Importa un PDF existente rasterizando sus páginas (permite editarlo, unirlo, hacer OCR...). */
    suspend fun importPdf(uri: Uri, name: String? = null): ScanDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            val id = newId()
            val pages = mutableListOf<Page>()
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: error("No se pudo abrir el PDF")
            pfd.use {
                PdfRenderer(it).use { renderer ->
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page ->
                            val scale = 200f / 72f // 200 ppp
                            val bmp = Bitmap.createBitmap(
                                (page.width * scale).toInt().coerceAtLeast(1),
                                (page.height * scale).toInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888,
                            )
                            Canvas(bmp).drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val fileName = newPageName()
                            ImageUtils.saveJpeg(bmp, pageFile(id, fileName))
                            bmp.recycle()
                            pages += Page(fileName)
                        }
                    }
                }
            }
            val now = System.currentTimeMillis()
            val doc = ScanDocument(id, name ?: defaultName("PDF importado"), now, now, pages)
            save(doc)
            doc
        }
    }

    suspend fun addPages(id: String, uris: List<Uri>): ScanDocument = edit(id) { doc ->
        doc.copy(pages = doc.pages + uris.map { importImage(id, it) })
    }

    // ------------------------------------------------------------------ edición de páginas

    suspend fun deletePages(id: String, files: Set<String>): ScanDocument = edit(id) { doc ->
        doc.pages.filter { it.file in files }.forEach { deletePageFiles(id, it) }
        doc.copy(pages = doc.pages.filterNot { it.file in files })
    }

    suspend fun movePage(id: String, file: String, delta: Int): ScanDocument = edit(id) { doc ->
        val list = doc.pages.toMutableList()
        val from = list.indexOfFirst { it.file == file }
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from >= 0 && from != to) list.add(to, list.removeAt(from))
        doc.copy(pages = list)
    }

    suspend fun rotatePage(id: String, file: String, degrees: Int): ScanDocument = edit(id) { doc ->
        doc.copy(pages = doc.pages.map { p ->
            if (p.file != file) return@map p
            val f = pageFile(id, p.file)
            ImageUtils.saveJpeg(ImageUtils.rotate(ImageUtils.decodeFile(f, Int.MAX_VALUE), degrees), f)
            // El original (si existe) también se rota para que "restaurar" conserve la orientación.
            p.original?.let { o ->
                val of = pageFile(id, o)
                ImageUtils.saveJpeg(ImageUtils.rotate(ImageUtils.decodeFile(of, Int.MAX_VALUE), degrees), of)
            }
            p.copy(ocr = null) // las cajas de OCR ya no son válidas
        })
    }

    suspend fun applyFilter(id: String, file: String, filter: PageFilter): ScanDocument = edit(id) { doc ->
        doc.copy(pages = doc.pages.map { p ->
            if (p.file != file) return@map p
            val current = pageFile(id, p.file)
            // Guarda el original la primera vez para poder deshacer.
            val originalName = p.original ?: newPageName().also { current.copyTo(pageFile(id, it)) }
            val src = ImageUtils.decodeFile(pageFile(id, originalName), Int.MAX_VALUE)
            val out = ImageUtils.applyFilter(src, filter)
            src.recycle()
            ImageUtils.saveJpeg(out, current)
            out.recycle()
            p.copy(original = originalName)
        })
    }

    suspend fun restoreOriginal(id: String, file: String): ScanDocument = edit(id) { doc ->
        doc.copy(pages = doc.pages.map { p ->
            if (p.file != file || p.original == null) return@map p
            val orig = pageFile(id, p.original)
            orig.copyTo(pageFile(id, p.file), overwrite = true)
            orig.delete()
            p.copy(original = null)
        })
    }

    suspend fun setOcr(id: String, file: String, ocr: OcrResult): ScanDocument? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val doc = get(id) ?: return@withLock null
            if (doc.pages.none { it.file == file }) return@withLock doc
            val updated = doc.copy(pages = doc.pages.map { if (it.file == file) it.copy(ocr = ocr) else it })
            // El OCR no cambia la fecha de modificación (no reordena la biblioteca).
            save(updated)
            updated
        }
    }

    // ------------------------------------------------------------------ documentos

    suspend fun rename(id: String, name: String): ScanDocument = edit(id) { it.copy(name = name.trim().ifEmpty { it.name }) }

    suspend fun delete(ids: Collection<String>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            ids.forEach { File(root, it).deleteRecursively() }
            _documents.value = _documents.value.filterNot { it.id in ids }
        }
    }

    /** Une varios documentos (en el orden dado) en uno nuevo. Los originales se conservan. */
    suspend fun merge(ids: List<String>, name: String): ScanDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            val newId = newId()
            val pages = ids.mapNotNull { get(it) }.flatMap { src -> src.pages.map { copyPage(src.id, newId, it) } }
            val now = System.currentTimeMillis()
            val doc = ScanDocument(newId, name, now, now, pages)
            save(doc)
            doc
        }
    }

    /** Copia las páginas indicadas a un documento nuevo (dividir / extraer). */
    suspend fun extract(id: String, files: Set<String>, name: String): ScanDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            val src = get(id) ?: error("Documento no encontrado")
            val newId = newId()
            val pages = src.pages.filter { it.file in files }.map { copyPage(src.id, newId, it) }
            val now = System.currentTimeMillis()
            val doc = ScanDocument(newId, name, now, now, pages)
            save(doc)
            doc
        }
    }

    // ------------------------------------------------------------------ internos

    private suspend fun edit(id: String, block: (ScanDocument) -> ScanDocument): ScanDocument =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val doc = get(id) ?: error("Documento no encontrado")
                val updated = block(doc).copy(updatedAt = System.currentTimeMillis())
                save(updated)
                updated
            }
        }

    private fun save(doc: ScanDocument) {
        val dir = File(root, doc.id).apply { mkdirs() }
        val tmp = File(dir, "$META.tmp")
        tmp.writeText(doc.toJson().toString())
        tmp.renameTo(File(dir, META)) || run { File(dir, META).writeText(tmp.readText()); tmp.delete() }
        _documents.value = (_documents.value.filterNot { it.id == doc.id } + doc).sortedByDescending { it.updatedAt }
    }

    private fun importImage(docId: String, uri: Uri): Page {
        val bmp = ImageUtils.decodeUri(context, uri, ImageUtils.STORE_MAX_PX)
        val name = newPageName()
        ImageUtils.saveJpeg(bmp, pageFile(docId, name))
        bmp.recycle()
        return Page(name)
    }

    private fun copyPage(fromId: String, toId: String, page: Page): Page {
        val name = newPageName()
        pageFile(fromId, page.file).copyTo(pageFile(toId, name))
        val original = page.original?.let { o -> newPageName().also { pageFile(fromId, o).copyTo(pageFile(toId, it)) } }
        return Page(name, page.ocr, original)
    }

    private fun deletePageFiles(docId: String, page: Page) {
        pageFile(docId, page.file).delete()
        page.original?.let { pageFile(docId, it).delete() }
    }

    private fun newId() = UUID.randomUUID().toString()
    private fun newPageName() = "p_${UUID.randomUUID().toString().take(12)}.jpg"

    companion object {
        private const val META = "meta.json"

        fun defaultName(prefix: String = "Escaneo"): String =
            "$prefix ${SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date())}"
    }
}
