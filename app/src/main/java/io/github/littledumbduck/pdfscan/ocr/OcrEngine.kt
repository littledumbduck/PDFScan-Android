package io.github.littledumbduck.pdfscan.ocr

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.littledumbduck.pdfscan.data.OcrLine
import io.github.littledumbduck.pdfscan.data.OcrResult
import io.github.littledumbduck.pdfscan.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** OCR en el dispositivo con ML Kit (alfabeto latino: español, inglés, francés, alemán, etc.). */
class OcrEngine {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun recognize(file: File): OcrResult {
        val bitmap = withContext(Dispatchers.IO) { ImageUtils.decodeFile(file, MAX_PX) }
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val lines = text.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrLine(line.text, box.left, box.top, box.right, box.bottom)
                }
            }
            return OcrResult(bitmap.width, bitmap.height, lines)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    private companion object {
        const val MAX_PX = 2500
    }
}
