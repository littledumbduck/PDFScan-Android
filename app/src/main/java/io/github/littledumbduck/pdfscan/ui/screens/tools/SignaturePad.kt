package io.github.littledumbduck.pdfscan.ui.screens.tools

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.max
import kotlin.math.min

private val InkColors = listOf(Color(0xFF111111), Color(0xFF0B3D91), Color(0xFF1565C0))

/** Panel para dibujar la firma con el dedo. Devuelve un PNG transparente recortado. */
@Composable
fun SignaturePadDialog(onDismiss: () -> Unit, onSave: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableStateOf(InkColors[1]) }
    var canvasW by remember { mutableStateOf(1) }
    val strokeDp = 3.dp

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Dibuja tu firma", style = MaterialTheme.typography.titleLarge)
                Box(
                    Modifier
                        .padding(vertical = 12.dp)
                        .fillMaxWidth()
                        .aspectRatio(2.2f)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
                ) {
                    Canvas(
                        Modifier
                            .matchParentSize()
                            .pointerInput(Unit) {
                                canvasW = size.width
                                detectDragGestures(
                                    onDragStart = { current = listOf(it) },
                                    onDragEnd = { if (current.size > 1) strokes += current; current = emptyList() },
                                    onDragCancel = { current = emptyList() },
                                ) { change, _ ->
                                    change.consume()
                                    current = current + change.position
                                }
                            },
                    ) {
                        val style = Stroke(width = strokeDp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        (strokes + listOf(current)).forEach { pts ->
                            if (pts.size > 1) drawPath(pathOf(pts), color, style = style)
                        }
                        // Línea guía
                        drawLine(
                            Color.LightGray,
                            Offset(size.width * 0.08f, size.height * 0.78f),
                            Offset(size.width * 0.92f, size.height * 0.78f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Color:")
                    InkColors.forEach { c ->
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(c, CircleShape)
                                .border(if (c == color) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { strokes.clear() }) { Text("Borrar") }
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    TextButton(
                        enabled = strokes.isNotEmpty(),
                        onClick = { onSave(render(strokes, color, strokeWidthPx = canvasW / 110f)) },
                    ) { Text("Guardar firma") }
                }
            }
        }
    }
}

private fun pathOf(points: List<Offset>) = Path().apply {
    moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) {
        val p = points[i - 1]
        val q = points[i]
        quadraticTo(p.x, p.y, (p.x + q.x) / 2f, (p.y + q.y) / 2f)
    }
    lineTo(points.last().x, points.last().y)
}

/** Dibuja los trazos en un bitmap transparente recortado a su contenido (a 2x para más nitidez). */
private fun render(strokes: List<List<Offset>>, color: Color, strokeWidthPx: Float): Bitmap {
    val scale = 2f
    val all = strokes.flatten()
    val pad = strokeWidthPx * 2
    val minX = all.minOf { it.x } - pad
    val minY = all.minOf { it.y } - pad
    val maxX = all.maxOf { it.x } + pad
    val maxY = all.maxOf { it.y } + pad
    val w = max(1, ((maxX - minX) * scale).toInt())
    val h = max(1, ((maxY - minY) * scale).toInt())
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        style = Paint.Style.STROKE
        strokeWidth = max(2f, min(strokeWidthPx, 12f)) * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    strokes.forEach { pts ->
        val path = android.graphics.Path()
        path.moveTo((pts[0].x - minX) * scale, (pts[0].y - minY) * scale)
        for (i in 1 until pts.size) {
            val p = pts[i - 1]
            val q = pts[i]
            path.quadTo((p.x - minX) * scale, (p.y - minY) * scale, ((p.x + q.x) / 2 - minX) * scale, ((p.y + q.y) / 2 - minY) * scale)
        }
        path.lineTo((pts.last().x - minX) * scale, (pts.last().y - minY) * scale)
        canvas.drawPath(path, paint)
    }
    return bmp
}
