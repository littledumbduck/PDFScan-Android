package io.github.littledumbduck.pdfscan.ui

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.littledumbduck.pdfscan.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Caché de miniaturas en memoria (clave: ruta + fecha de modificación + tamaño). */
private object ThumbCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
}

/** Muestra un JPG del almacenamiento interno cargándolo en segundo plano. */
@Composable
fun FileImage(
    file: File,
    modifier: Modifier = Modifier,
    maxPx: Int = 600,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val key = "${file.absolutePath}:${file.lastModified()}:$maxPx"
    val bitmap by produceState(ThumbCache.cache.get(key), key) {
        // La clave cambia al rotar/filtrar una página: siempre se recalcula.
        value = ThumbCache.cache.get(key) ?: withContext(Dispatchers.IO) {
            runCatching { ImageUtils.decodeFile(file, maxPx) }.getOrNull()
        }?.also { ThumbCache.cache.put(key, it) }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bmp.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = contentScale)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

@Composable
fun BusyOverlay(message: String) {
    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = null,
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(message)
            }
        },
    )
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String = "Aceptar",
    label: String? = null,
    password: Boolean = false,
    extraAction: Pair<String, () -> String?>? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = if (label != null) ({ Text(label) }) else null,
                    singleLine = true,
                    visualTransformation = if (password) PasswordVisualTransformation()
                    else androidx.compose.ui.text.input.VisualTransformation.None,
                    keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password)
                    else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                extraAction?.let { (actionLabel, action) ->
                    TextButton(onClick = {
                        action()?.let { s -> value = TextFieldValue(s, TextRange(s.length)) }
                    }) { Text(actionLabel) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
