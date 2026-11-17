package io.github.littledumbduck.pdfscan.tools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Elemento colocado sobre una página (firma o texto). Coordenadas normalizadas 0..1 desde arriba-izquierda. */
data class Stamp(
    val page: Int,
    val u: Float,
    val v: Float,
    val w: Float,
    val h: Float,
    val signature: File? = null,
    val text: String? = null,
)

/** Operación de organizar: página original [source] con rotación adicional [rotation]. */
data class PageOp(val source: Int, val rotation: Int = 0)

enum class CompressLevel(val label: String, val description: String, val maxPx: Int, val quality: Float) {
    LOW("Ligera", "Casi sin pérdida de calidad", 2480, 0.85f),
    MEDIUM("Recomendada", "Buen equilibrio calidad / tamaño", 1754, 0.7f),
    HIGH("Máxima", "Archivo muy pequeño, menos calidad", 1240, 0.5f),
}

enum class NumberPosition(val label: String) { BOTTOM_CENTER("Abajo centro"), BOTTOM_RIGHT("Abajo derecha"), TOP_RIGHT("Arriba derecha") }
enum class NumberFormat(val label: String) { N("1"), N_OF_TOTAL("1 / 10"), PAGE_N("Página 1") }

class PdfPasswordException : Exception("El PDF está protegido con contraseña")

/**
 * Operaciones sobre PDFs existentes con PdfBox-Android. Todo ocurre en el dispositivo;
 * el contenido vectorial y el texto de los PDFs se conservan (no se rasteriza nada salvo en "a imágenes").
 */
object PdfTools {

    /**
     * Abre un PDF. Si está cifrado y se proporciona contraseña (o solo tiene contraseña de propietario),
     * se devuelve un documento sin cifrado. Lanza [PdfPasswordException] si hace falta contraseña.
     */
    fun open(file: File, password: String? = null): PDDocument {
        val doc = try {
            if (password != null) PDDocument.load(file, password) else PDDocument.load(file)
        } catch (_: InvalidPasswordException) {
            throw PdfPasswordException()
        }
        if (doc.isEncrypted) doc.isAllSecurityToBeRemoved = true
        return doc
    }

    /** Copia "limpia" (sin cifrado) de [src] en [dst]. Devuelve el número de páginas. */
    suspend fun normalize(src: File, dst: File, password: String?): Int = io {
        open(src, password).use { doc ->
            doc.save(dst)
            doc.numberOfPages
        }
    }

    suspend fun pageCount(file: File): Int = io { open(file).use { it.numberOfPages } }

    suspend fun merge(inputs: List<File>, out: File) = io {
        PDDocument().use { dest ->
            val merger = PDFMergerUtility()
            val opened = mutableListOf<PDDocument>()
            try {
                for (f in inputs) {
                    val src = open(f)
                    opened += src
                    merger.appendDocument(dest, src)
                }
                dest.save(out)
            } finally {
                opened.forEach { it.close() }
            }
        }
    }

    /** Divide en varios PDFs; cada rango es una lista de índices (0-based). */
    suspend fun split(input: File, ranges: List<List<Int>>, outFor: (Int, List<Int>) -> File): List<File> = io {
        open(input).use { src ->
            ranges.mapIndexed { i, pages ->
                val out = outFor(i, pages)
                PDDocument().use { dest ->
                    pages.forEach { p -> dest.importPage(src.getPage(p)) }
                    dest.save(out)
                }
                out
            }
        }
    }

    /** Crea un PDF con las páginas en el orden de [ops], aplicando rotaciones. */
    suspend fun organize(input: File, ops: List<PageOp>, out: File) = io {
        open(input).use { src ->
            PDDocument().use { dest ->
                for (op in ops) {
                    val page = dest.importPage(src.getPage(op.source))
                    page.rotation = ((page.rotation + op.rotation) % 360 + 360) % 360
                }
                dest.save(out)
            }
        }
    }

    /** Recomprime las imágenes del PDF. Devuelve (tamaño antes, tamaño después). */
    suspend fun compress(input: File, level: CompressLevel, out: File): Pair<Long, Long> = io {
        open(input).use { doc ->
            for (page in doc.pages) {
                val res = page.resources ?: continue
                for (name in res.xObjectNames.toList()) {
                    val img = res.getXObject(name) as? PDImageXObject ?: continue
                    recompress(doc, img, level)?.let { res.put(name, it) }
                }
            }
            doc.save(out)
        }
        input.length() to out.length()
    }

    private fun recompress(doc: PDDocument, img: PDImageXObject, level: CompressLevel): PDImageXObject? = try {
        if (img.isStencil || img.softMask != null || img.width * img.height < 200 * 200) {
            null
        } else {
            val bmp: Bitmap = img.image
            val longSide = max(bmp.width, bmp.height)
            val scaled = if (longSide > level.maxPx) {
                val f = level.maxPx.toFloat() / longSide
                Bitmap.createScaledBitmap(bmp, (bmp.width * f).roundToInt(), (bmp.height * f).roundToInt(), true)
            } else bmp
            JPEGFactory.createFromImage(doc, scaled, level.quality)
        }
    } catch (_: Throwable) {
        null // formato de imagen no soportado: se deja tal cual
    }

    suspend fun protect(input: File, password: String, out: File) = io {
        open(input).use { doc ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 256
            doc.protect(policy)
            doc.save(out)
        }
    }

    /** Quita la contraseña (hay que conocerla). */
    suspend fun unlock(input: File, password: String, out: File) = io {
        open(input, password).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        }
    }

    suspend fun extractText(input: File): String = io {
        open(input).use { PDFTextStripper().getText(it) }
    }

    /** Coloca firmas y textos sobre las páginas. */
    suspend fun stamp(input: File, stamps: List<Stamp>, out: File) = io {
        open(input).use { doc ->
            val images = mutableMapOf<File, PDImageXObject>()
            val font: PDFont = PDType1Font.HELVETICA
            for ((pageIndex, items) in stamps.groupBy { it.page }) {
                val page = doc.getPage(pageIndex)
                val (wd, hd) = displaySize(page)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.saveGraphicsState()
                    cs.transform(displayMatrix(page))
                    for (s in items) {
                        val x = s.u * wd
                        val w = s.w * wd
                        val h = s.h * hd
                        val y = (1f - s.v - s.h) * hd
                        if (s.signature != null) {
                            val img = images.getOrPut(s.signature) {
                                val bmp = BitmapFactory.decodeFile(s.signature.absolutePath)
                                LosslessFactory.createFromImage(doc, bmp)
                            }
                            cs.drawImage(img, x, y, w, h)
                        } else if (!s.text.isNullOrBlank()) {
                            val text = sanitize(font, s.text)
                            val size = h * 0.8f
                            cs.beginText()
                            cs.setNonStrokingColor(0.05f, 0.1f, 0.35f)
                            cs.setFont(font, size)
                            cs.newLineAtOffset(x, y + h * 0.2f)
                            cs.showText(text)
                            cs.endText()
                        }
                    }
                    cs.restoreGraphicsState()
                }
            }
            doc.save(out)
        }
    }

    suspend fun pageNumbers(input: File, position: NumberPosition, format: NumberFormat, out: File) = io {
        open(input).use { doc ->
            val font: PDFont = PDType1Font.HELVETICA
            val total = doc.numberOfPages
            doc.pages.forEachIndexed { i, page ->
                val (wd, hd) = displaySize(page)
                val label = when (format) {
                    NumberFormat.N -> "${i + 1}"
                    NumberFormat.N_OF_TOTAL -> "${i + 1} / $total"
                    NumberFormat.PAGE_N -> "Página ${i + 1}"
                }.let { sanitize(font, it) }
                val size = (minOf(wd, hd) / 50f).coerceIn(8f, 14f)
                val textW = font.getStringWidth(label) / 1000f * size
                val margin = size * 2f
                val (x, y) = when (position) {
                    NumberPosition.BOTTOM_CENTER -> (wd - textW) / 2f to margin
                    NumberPosition.BOTTOM_RIGHT -> wd - textW - margin to margin
                    NumberPosition.TOP_RIGHT -> wd - textW - margin to hd - margin - size
                }
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.saveGraphicsState()
                    cs.transform(displayMatrix(page))
                    cs.beginText()
                    cs.setNonStrokingColor(0.2f, 0.2f, 0.2f)
                    cs.setFont(font, size)
                    cs.newLineAtOffset(x, y)
                    cs.showText(label)
                    cs.endText()
                    cs.restoreGraphicsState()
                }
            }
            doc.save(out)
        }
    }

    suspend fun watermark(input: File, text: String, opacity: Float, out: File) = io {
        open(input).use { doc ->
            val font: PDFont = PDType1Font.HELVETICA_BOLD
            val label = sanitize(font, text)
            val gs = PDExtendedGraphicsState().apply { nonStrokingAlphaConstant = opacity }
            for (page in doc.pages) {
                val (wd, hd) = displaySize(page)
                val diagonal = kotlin.math.sqrt(wd * wd + hd * hd)
                val unitW = font.getStringWidth(label) / 1000f
                if (unitW <= 0f) continue
                val size = (diagonal * 0.7f / unitW).coerceAtMost(hd / 4f)
                val textW = unitW * size
                val angle = kotlin.math.atan2(hd, wd)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.saveGraphicsState()
                    cs.transform(displayMatrix(page))
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(0.6f, 0.6f, 0.6f)
                    cs.beginText()
                    cs.setFont(font, size)
                    // Centrado y en diagonal (abajo-izquierda → arriba-derecha)
                    val m = Matrix.getRotateInstance(angle.toDouble(), wd / 2f, hd / 2f)
                    m.translate(-textW / 2f, -size / 3f)
                    cs.setTextMatrix(m)
                    cs.showText(label)
                    cs.endText()
                    cs.restoreGraphicsState()
                }
            }
            doc.save(out)
        }
    }

    // ------------------------------------------------------------------ geometría

    private fun rotation(page: PDPage) = ((page.rotation % 360) + 360) % 360

    /** Tamaño de la página tal como se ve (con la rotación aplicada). */
    fun displaySize(page: PDPage): Pair<Float, Float> {
        val box = page.cropBox
        return if (rotation(page) % 180 == 90) box.height to box.width else box.width to box.height
    }

    /**
     * Matriz que convierte el "espacio visible" (origen abajo-izquierda de la página tal como se ve,
     * con la rotación aplicada) al espacio de usuario del PDF. Así se puede dibujar sin preocuparse de /Rotate.
     */
    private fun displayMatrix(page: PDPage): Matrix {
        val box = page.cropBox
        val w = box.width
        val h = box.height
        val ox = box.lowerLeftX
        val oy = box.lowerLeftY
        return when (rotation(page)) {
            90 -> Matrix(0f, 1f, -1f, 0f, w + ox, oy)
            180 -> Matrix(-1f, 0f, 0f, -1f, w + ox, h + oy)
            270 -> Matrix(0f, -1f, 1f, 0f, ox, h + oy)
            else -> Matrix(1f, 0f, 0f, 1f, ox, oy)
        }
    }

    private fun sanitize(font: PDFont, text: String): String = buildString {
        for (ch in text) {
            val ok = ch != '\n' && ch != '\r' && try {
                font.encode(ch.toString()); true
            } catch (_: Exception) {
                false
            }
            append(if (ok) ch else ' ')
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}
