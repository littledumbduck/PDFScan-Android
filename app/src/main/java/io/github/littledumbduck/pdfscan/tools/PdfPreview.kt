package io.github.littledumbduck.pdfscan.tools

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import io.github.littledumbduck.pdfscan.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Renderizado de páginas de PDF con el PdfRenderer del sistema (serializado: no es thread-safe). */
object PdfPreview {

    private val mutex = Mutex()
    private var openFile: String? = null
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    private fun key(file: File, index: Int, widthPx: Int) = "${file.absolutePath}:${file.lastModified()}:$index:$widthPx"

    fun cached(file: File, index: Int, widthPx: Int): Bitmap? = cache.get(key(file, index, widthPx))

    /** Tamaño (ancho, alto) en puntos de cada página, tal como se ve. */
    suspend fun pageSizes(file: File): List<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val r = rendererFor(file)
            (0 until r.pageCount).map { i -> r.openPage(i).use { it.width to it.height } }
        }
    }

    suspend fun render(file: File, index: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        cached(file, index, widthPx)?.let { return@withContext it }
        mutex.withLock {
            runCatching {
                val r = rendererFor(file)
                if (index !in 0 until r.pageCount) return@runCatching null
                r.openPage(index).use { page ->
                    val w = widthPx.coerceAtLeast(1)
                    val h = (w.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }.getOrNull()?.also { cache.put(key(file, index, widthPx), it) }
        }
    }

    /** Exporta cada página como JPG (≈ [dpi] ppp). */
    suspend fun toImages(file: File, dpi: Int, outFor: (Int) -> File): List<File> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val r = rendererFor(file)
            (0 until r.pageCount).map { i ->
                r.openPage(i).use { page ->
                    val scale = dpi / 72f
                    val bmp = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1),
                        Bitmap.Config.ARGB_8888,
                    )
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                    val out = outFor(i)
                    ImageUtils.saveJpeg(bmp, out, 90)
                    bmp.recycle()
                    out
                }
            }
        }
    }

    /** Libera el renderer (p. ej. antes de sobrescribir o borrar el archivo). */
    suspend fun close() = mutex.withLock { closeLocked() }

    private fun rendererFor(file: File): PdfRenderer {
        val id = "${file.absolutePath}:${file.lastModified()}"
        renderer?.let { if (openFile == id) return it }
        closeLocked()
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(fd)
        pfd = fd
        renderer = r
        openFile = id
        return r
    }

    private fun closeLocked() {
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        renderer = null
        pfd = null
        openFile = null
    }
}

/** Imagen de una página de PDF. */
@Composable
fun PdfPageImage(file: File, index: Int, widthPx: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState(PdfPreview.cached(file, index, widthPx), file, file.lastModified(), index, widthPx) {
        // Las claves pueden cambiar (otro archivo/página): siempre se recalcula.
        value = PdfPreview.cached(file, index, widthPx) ?: run {
            value = null
            PdfPreview.render(file, index, widthPx)
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = "Página ${index + 1}", modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
