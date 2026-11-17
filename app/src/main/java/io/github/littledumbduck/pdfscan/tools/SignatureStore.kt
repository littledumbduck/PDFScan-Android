package io.github.littledumbduck.pdfscan.tools

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Firmas guardadas como PNG transparentes para reutilizarlas. */
class SignatureStore(context: Context) {

    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }
    private val _signatures = MutableStateFlow(list())
    val signatures: StateFlow<List<File>> = _signatures.asStateFlow()

    private fun list() = dir.listFiles { f -> f.extension == "png" }.orEmpty().sortedByDescending { it.lastModified() }

    suspend fun save(bitmap: Bitmap): File = withContext(Dispatchers.IO) {
        val f = File(dir, "firma_${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        _signatures.value = list()
        f
    }

    fun delete(file: File) {
        file.delete()
        _signatures.value = list()
    }
}
