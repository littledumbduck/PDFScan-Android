package io.github.littledumbduck.pdfscan

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import io.github.littledumbduck.pdfscan.data.DocumentRepository
import io.github.littledumbduck.pdfscan.data.SettingsStore
import io.github.littledumbduck.pdfscan.ocr.OcrEngine
import io.github.littledumbduck.pdfscan.pdf.PdfExporter

/** Contenedor de dependencias sencillo (la app es pequeña, no hace falta Hilt). */
class PdfScanApp : Application() {

    lateinit var repository: DocumentRepository
        private set
    lateinit var settings: SettingsStore
        private set
    val ocr = OcrEngine()
    val pdfExporter = PdfExporter()

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
        repository = DocumentRepository(this)
        settings = SettingsStore(this)
    }
}
