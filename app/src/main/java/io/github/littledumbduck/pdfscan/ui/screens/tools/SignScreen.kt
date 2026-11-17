package io.github.littledumbduck.pdfscan.ui.screens.tools

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.data.ScanDocument
import io.github.littledumbduck.pdfscan.tools.InputTarget
import io.github.littledumbduck.pdfscan.tools.PdfPageImage
import io.github.littledumbduck.pdfscan.tools.PdfPreview
import io.github.littledumbduck.pdfscan.tools.Stamp
import io.github.littledumbduck.pdfscan.tools.ToolsViewModel
import io.github.littledumbduck.pdfscan.ui.TextInputDialog
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private data class PlacedItem(val id: Long, val stamp: Stamp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignScreen(
    tvm: ToolsViewModel,
    scans: List<ScanDocument>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
) {
    val input by tvm.input.collectAsStateWithLifecycle()
    val signatures by tvm.signatures.signatures.collectAsStateWithLifecycle()
    val pdf = input
    var pickScan by rememberSaveable { mutableStateOf(false) }

    val items = remember(pdf) { mutableStateListOf<PlacedItem>() }
    var selectedId by remember(pdf) { mutableStateOf<Long?>(null) }
    var pageIndex by remember(pdf) { mutableIntStateOf(0) }
    var showSignatures by remember { mutableStateOf(false) }
    var showPad by remember { mutableStateOf(false) }
    var addingText by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pageSizes by produceState<List<Pair<Int, Int>>>(emptyList(), pdf) {
        value = pdf?.let { runCatching { PdfPreview.pageSizes(it.file) }.getOrNull() } ?: emptyList()
    }
    val pageAspect = pageSizes.getOrNull(pageIndex)?.let { it.first.toFloat() / it.second } ?: 0.707f

    fun addSignature(file: File) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        val imgAspect = if (o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else 3f
        val w = 0.35f
        val h = w * pageAspect / imgAspect
        val item = PlacedItem(System.nanoTime(), Stamp(pageIndex, 0.55f, 0.75f, w, h, signature = file))
        items += item
        selectedId = item.id
    }

    fun addText(text: String) {
        val item = PlacedItem(System.nanoTime(), Stamp(pageIndex, 0.1f, 0.1f, 0f, 0.025f, text = text))
        items += item
        selectedId = item.id
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text("Firmar") },
                actions = {
                    if (selectedId != null) {
                        IconButton(onClick = { items.removeAll { it.id == selectedId }; selectedId = null }) {
                            Icon(Icons.Default.Delete, "Quitar elemento")
                        }
                    }
                    if (pdf != null) {
                        TextButton(enabled = items.isNotEmpty(), onClick = { tvm.sign(items.map { it.stamp }) }) { Text("Guardar") }
                    }
                },
            )
        },
        bottomBar = {
            if (pdf != null) {
                BottomAppBar {
                    IconButton(onClick = { pageIndex--; selectedId = null }, enabled = pageIndex > 0) {
                        Icon(Icons.Default.ChevronLeft, "Página anterior")
                    }
                    Text("${pageIndex + 1}/${pdf.pages}", style = MaterialTheme.typography.labelLarge)
                    IconButton(onClick = { pageIndex++; selectedId = null }, enabled = pageIndex < pdf.pages - 1) {
                        Icon(Icons.Default.ChevronRight, "Página siguiente")
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { if (signatures.isEmpty()) showPad = true else showSignatures = true }) {
                            Icon(Icons.Default.Draw, "Añadir firma")
                        }
                        IconButton(onClick = { addingText = true }) { Icon(Icons.Default.TextFields, "Añadir texto") }
                        IconButton(onClick = {
                            addText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date()))
                        }) { Icon(Icons.Default.CalendarToday, "Añadir fecha") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (pdf == null) {
                InputPicker(null, onPickFile, { pickScan = true }, tvm::clearInput)
                return@Column
            }
            Text(
                "Añade tu firma, un texto o la fecha. Arrástralos para moverlos y usa la esquina para cambiar el tamaño.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            BoxWithConstraints(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest).padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val density = LocalDensity.current
                val maxW = constraints.maxWidth.toFloat()
                val maxH = constraints.maxHeight.toFloat()
                val (pw, ph) = if (maxW / maxH > pageAspect) maxH * pageAspect to maxH else maxW to maxW / pageAspect
                Box(
                    Modifier
                        .size(with(density) { pw.toDp() }, with(density) { ph.toDp() })
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        .pointerInput(Unit) { detectTapGestures { selectedId = null } },
                ) {
                    PdfPageImage(pdf.file, pageIndex, pw.roundToInt(), Modifier.fillMaxSize())
                    items.filter { it.stamp.page == pageIndex }.forEach { item ->
                        PlacedItemView(
                            item = item,
                            pageW = pw,
                            pageH = ph,
                            selected = item.id == selectedId,
                            onSelect = { selectedId = item.id },
                            onChange = { s ->
                                val i = items.indexOfFirst { it.id == item.id }
                                if (i >= 0) items[i] = item.copy(stamp = s)
                            },
                        )
                    }
                }
            }
        }
    }

    if (pickScan) {
        ScanPickerDialog(scans, multiple = false, onDismiss = { pickScan = false }, onPick = { tvm.pickScans(it, InputTarget.SINGLE) })
    }
    if (showSignatures) {
        AlertDialog(
            onDismissRequest = { showSignatures = false },
            title = { Text("Tus firmas") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(signatures, key = { it.absolutePath }) { f ->
                        val bmp = remember(f) { BitmapFactory.decodeFile(f.absolutePath) }
                        ListItem(
                            headlineContent = {
                                if (bmp != null) Image(
                                    bmp.asImageBitmap(), "Firma",
                                    modifier = Modifier.height(56.dp).fillMaxWidth().background(Color.White),
                                    contentScale = ContentScale.Fit,
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = { tvm.signatures.delete(f) }) { Icon(Icons.Default.Delete, "Borrar firma") }
                            },
                            modifier = Modifier.clickable { showSignatures = false; addSignature(f) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSignatures = false; showPad = true }) {
                    Icon(Icons.Default.Add, null); Text(" Nueva firma")
                }
            },
            dismissButton = { TextButton(onClick = { showSignatures = false }) { Text("Cancelar") } },
        )
    }
    if (showPad) {
        SignaturePadDialog(
            onDismiss = { showPad = false },
            onSave = { bmp ->
                showPad = false
                scope.launch { addSignature(tvm.signatures.save(bmp)) }
            },
        )
    }
    if (addingText) {
        TextInputDialog(
            title = "Añadir texto",
            initial = "",
            label = "Texto (nombre, DNI, \"Leído y conforme\"…)",
            confirmLabel = "Añadir",
            onDismiss = { addingText = false },
            onConfirm = { addingText = false; addText(it) },
        )
    }
}

@Composable
private fun PlacedItemView(
    item: PlacedItem,
    pageW: Float,
    pageH: Float,
    selected: Boolean,
    onSelect: () -> Unit,
    onChange: (Stamp) -> Unit,
) {
    val density = LocalDensity.current
    val s = item.stamp
    val current by rememberUpdatedState(s)
    val sig = s.signature
    val bitmap = remember(sig) { sig?.let { BitmapFactory.decodeFile(it.absolutePath) } }
    val heightDp = with(density) { (s.h * pageH).toDp() }
    val border = if (selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary) else Modifier

    Box(
        Modifier
            .offset { IntOffset((s.u * pageW).roundToInt(), (s.v * pageH).roundToInt()) }
            .then(border)
            .pointerInput(item.id) {
                detectDragGestures(onDragStart = { onSelect() }) { change, drag ->
                    change.consume()
                    val c = current
                    val maxU = if (c.signature != null) 1f - c.w else 0.95f
                    onChange(
                        c.copy(
                            u = (c.u + drag.x / pageW).coerceIn(0f, maxU.coerceAtLeast(0f)),
                            v = (c.v + drag.y / pageH).coerceIn(0f, (1f - c.h).coerceAtLeast(0f)),
                        ),
                    )
                }
            }
            .pointerInput(item.id) { detectTapGestures { onSelect() } },
    ) {
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(), "Firma",
                modifier = Modifier.size(with(density) { (s.w * pageW).toDp() }, heightDp),
                contentScale = ContentScale.FillBounds,
            )
        } else {
            val fontSize = with(density) { (s.h * pageH * 0.8f).toSp() }
            Text(
                s.text.orEmpty(),
                color = Color(0xFF0D1A59),
                fontSize = fontSize,
                lineHeight = fontSize,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.height(heightDp).wrapContentWidth(unbounded = true),
            )
        }
        if (selected) {
            // Asa para redimensionar (mantiene la proporción)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(12.dp, 12.dp)
                    .size(26.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .pointerInput(item.id) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            val c = current
                            val newH = (c.h + drag.y / pageH).coerceIn(0.012f, 0.5f)
                            val ratio = newH / c.h
                            val newW = if (c.signature != null) (c.w * ratio).coerceAtMost(1f - c.u) else 0f
                            if (c.signature == null || newW < 1f - c.u) onChange(c.copy(h = newH, w = newW))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.OpenInFull, "Redimensionar", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp).width(14.dp))
            }
        }
    }
}
