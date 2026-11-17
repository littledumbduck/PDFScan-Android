package io.github.littledumbduck.pdfscan.pdf

import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import io.github.littledumbduck.pdfscan.data.AppSettings
import io.github.littledumbduck.pdfscan.data.OcrResult
import io.github.littledumbduck.pdfscan.data.PageSize
import io.github.littledumbduck.pdfscan.data.ScanDocument
import io.github.littledumbduck.pdfscan.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Calendar
import kotlin.math.min

/**
 * Genera PDFs con PdfBox-Android:
 * - imágenes JPEG recomprimidas según la calidad elegida
 * - capa de texto invisible a partir del OCR (PDF buscable y con texto seleccionable)
 * - cifrado AES-256 opcional con contraseña
 */
class PdfExporter {

    suspend fun export(
        doc: ScanDocument,
        pageFile: (String) -> File,
        settings: AppSettings,
        out: File,
        password: String? = null,
    ) = withContext(Dispatchers.IO) {
        PDDocument().use { pdf ->
            pdf.documentInformation = PDDocumentInformation().apply {
                title = doc.name
                creator = "PDFScan"
                producer = "PDFScan"
                creationDate = Calendar.getInstance()
            }

            for (page in doc.pages) {
                val src = pageFile(page.file)
                val bmp = ImageUtils.decodeFile(src, settings.quality.maxPx)
                val jpeg = ByteArrayOutputStream().use { bos ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, settings.quality.jpeg, bos)
                    bos.toByteArray()
                }
                val imgW = bmp.width.toFloat()
                val imgH = bmp.height.toFloat()
                bmp.recycle()

                // Tamaño de página
                val landscape = imgW > imgH
                val (pageW, pageH) = when (settings.pageSize) {
                    PageSize.AUTO -> {
                        // La imagen ocupa toda la página; ancho normalizado a A4 para un tamaño "físico" razonable.
                        val w = if (landscape) PageSize.A4.heightPt else PageSize.A4.widthPt
                        w to w * imgH / imgW
                    }
                    else -> if (landscape) settings.pageSize.heightPt to settings.pageSize.widthPt
                    else settings.pageSize.widthPt to settings.pageSize.heightPt
                }
                val margin = if (settings.pageSize != PageSize.AUTO && settings.margins) 24f else 0f
                val scale = min((pageW - 2 * margin) / imgW, (pageH - 2 * margin) / imgH)
                val drawW = imgW * scale
                val drawH = imgH * scale
                val x0 = (pageW - drawW) / 2f
                val y0 = (pageH - drawH) / 2f

                val pdPage = PDPage(PDRectangle(pageW, pageH))
                pdf.addPage(pdPage)
                val image = JPEGFactory.createFromStream(pdf, ByteArrayInputStream(jpeg))

                PDPageContentStream(pdf, pdPage).use { cs ->
                    cs.drawImage(image, x0, y0, drawW, drawH)
                    val ocr = page.ocr
                    if (settings.searchablePdf && ocr != null && ocr.lines.isNotEmpty()) {
                        writeInvisibleText(cs, ocr, x0, y0, drawW, drawH)
                    }
                }
            }

            if (!password.isNullOrEmpty()) {
                val policy = StandardProtectionPolicy(password, password, AccessPermission())
                policy.encryptionKeyLength = 256
                pdf.protect(policy)
            }

            out.parentFile?.mkdirs()
            pdf.save(out)
        }
    }

    /** Escribe cada línea de OCR como texto invisible encima de su posición en la imagen. */
    private fun writeInvisibleText(
        cs: PDPageContentStream,
        ocr: OcrResult,
        x0: Float, y0: Float, drawW: Float, drawH: Float,
    ) {
        val font: PDFont = PDType1Font.HELVETICA
        val sx = drawW / ocr.width
        val sy = drawH / ocr.height
        cs.beginText()
        cs.setRenderingMode(RenderingMode.NEITHER)
        for (line in ocr.lines) {
            val text = sanitize(font, line.text)
            if (text.isBlank()) continue
            val boxW = (line.right - line.left) * sx
            val boxH = (line.bottom - line.top) * sy
            if (boxW <= 0f || boxH <= 0f) continue
            val fontSize = boxH * 0.85f
            val textW = font.getStringWidth(text) / 1000f * fontSize
            if (textW <= 0f) continue
            cs.setFont(font, fontSize)
            cs.setHorizontalScaling(boxW / textW * 100f)
            // Origen de PDF abajo-izquierda; la línea base queda ligeramente por encima del borde inferior.
            val x = x0 + line.left * sx
            val y = y0 + drawH - line.bottom * sy + boxH * 0.15f
            cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(x, y))
            cs.showText(text)
        }
        cs.endText()
    }

    /** Sustituye los caracteres que la fuente estándar (WinAnsi) no puede codificar. */
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
}
