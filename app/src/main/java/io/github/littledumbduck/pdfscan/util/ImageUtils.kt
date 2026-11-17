package io.github.littledumbduck.pdfscan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class PageFilter(val label: String) {
    ENHANCE("Mejorar"),
    GRAYSCALE("Escala de grises"),
    BLACK_WHITE("Blanco y negro"),
    INK_SAVER("Ahorro de tinta"),
}

object ImageUtils {

    /** Lado largo máximo con el que se guardan las páginas (≈ A4 a 300 ppp). */
    const val STORE_MAX_PX = 3508
    const val STORE_JPEG_QUALITY = 92

    /** Decodifica una imagen de un [Uri] aplicando la orientación EXIF y reduciéndola a [maxPx]. */
    fun decodeUri(context: Context, uri: Uri, maxPx: Int): Bitmap {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
        val bmp = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: error("No se pudo leer la imagen")
        val rotation = resolver.openInputStream(uri).use { exifRotation(it) }
        return scaleDown(rotate(bmp, rotation), maxPx)
    }

    fun decodeFile(file: File, maxPx: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val sample = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("No se pudo leer ${file.name}")
        return scaleDown(bmp, maxPx)
    }

    /** Dimensiones de la imagen sin decodificarla. */
    fun imageSize(file: File): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        return o.outWidth to o.outHeight
    }

    fun saveJpeg(bitmap: Bitmap, file: File, quality: Int = STORE_JPEG_QUALITY) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        val out = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (out != bitmap) bitmap.recycle()
        return out
    }

    fun applyFilter(src: Bitmap, filter: PageFilter): Bitmap {
        val matrix = when (filter) {
            PageFilter.ENHANCE -> contrast(1.35f, 12f).apply { postConcat(saturation(1.15f)) }
            PageFilter.GRAYSCALE -> saturation(0f).apply { postConcat(contrast(1.15f, 6f)) }
            PageFilter.BLACK_WHITE -> saturation(0f).apply { postConcat(contrast(3.2f, 20f)) }
            PageFilter.INK_SAVER -> saturation(0f).apply { postConcat(contrast(1.8f, 60f)) }
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            })
        }
        return out
    }

    /** out = (in - 128) * c + 128 + b */
    private fun contrast(c: Float, b: Float): ColorMatrix {
        val t = (1f - c) * 128f + b
        return ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }

    private fun saturation(s: Float) = ColorMatrix().apply { setSaturation(s) }

    private fun sampleSize(w: Int, h: Int, maxPx: Int): Int {
        var sample = 1
        val longSide = max(w, h)
        while (longSide / (sample * 2) >= maxPx) sample *= 2
        return sample
    }

    private fun scaleDown(bmp: Bitmap, maxPx: Int): Bitmap {
        val longSide = max(bmp.width, bmp.height)
        if (longSide <= maxPx) return bmp
        val f = maxPx.toFloat() / longSide
        val out = Bitmap.createScaledBitmap(bmp, (bmp.width * f).roundToInt(), (bmp.height * f).roundToInt(), true)
        if (out != bmp) bmp.recycle()
        return out
    }

    private fun exifRotation(stream: InputStream?): Int {
        if (stream == null) return 0
        return try {
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (_: Exception) {
            0
        }
    }
}
