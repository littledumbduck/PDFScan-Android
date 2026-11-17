package io.github.littledumbduck.pdfscan.ui.screens.tools

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.data.ScanDocument
import io.github.littledumbduck.pdfscan.tools.InputTarget
import io.github.littledumbduck.pdfscan.tools.PageOp
import io.github.littledumbduck.pdfscan.tools.PdfPageImage
import io.github.littledumbduck.pdfscan.tools.ToolsViewModel

private data class OrgItem(val id: Int, val op: PageOp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    tvm: ToolsViewModel,
    scans: List<ScanDocument>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
) {
    val input by tvm.input.collectAsStateWithLifecycle()
    var pickScan by rememberSaveable { mutableStateOf(false) }
    val pdf = input

    var items by remember(pdf) { mutableStateOf((0 until (pdf?.pages ?: 0)).map { OrgItem(it, PageOp(it)) }) }
    var selected by remember(pdf) { mutableStateOf(setOf<Int>()) }
    val single = selected.singleOrNull()?.let { id -> items.indexOfFirst { it.id == id } }
    val changed = items.map { it.op } != (0 until (pdf?.pages ?: 0)).map { PageOp(it) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text(if (selected.isEmpty()) "Organizar páginas" else "${selected.size} seleccionadas") },
                actions = {
                    if (selected.isNotEmpty()) {
                        IconButton(onClick = {
                            items = items.map { if (it.id in selected) it.copy(op = it.op.copy(rotation = (it.op.rotation + 90) % 360)) else it }
                        }) { Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotar") }
                        IconButton(enabled = single != null && single > 0, onClick = {
                            val i = single!!
                            items = items.toMutableList().apply { add(i - 1, removeAt(i)) }
                        }) { Icon(Icons.Default.ChevronLeft, "Mover antes") }
                        IconButton(enabled = single != null && single < items.lastIndex, onClick = {
                            val i = single!!
                            items = items.toMutableList().apply { add(i + 1, removeAt(i)) }
                        }) { Icon(Icons.Default.ChevronRight, "Mover después") }
                        IconButton(onClick = {
                            tvm.extractPages(items.filter { it.id in selected }.map { it.op.source })
                        }) { Icon(Icons.Default.ContentCut, "Extraer a un PDF nuevo") }
                        IconButton(onClick = {
                            items = items.filterNot { it.id in selected }; selected = emptySet()
                        }) { Icon(Icons.Default.Delete, "Eliminar") }
                    }
                },
            )
        },
        bottomBar = {
            if (pdf != null) {
                BottomAppBar {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = changed,
                            onClick = { items = (0 until pdf.pages).map { OrgItem(it, PageOp(it)) }; selected = emptySet() },
                            modifier = Modifier.weight(1f),
                        ) { Text("Deshacer todo") }
                        Button(
                            enabled = changed && items.isNotEmpty(),
                            onClick = { tvm.organize(items.map { it.op }) },
                            modifier = Modifier.weight(1f),
                        ) { Text("Guardar") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            InputPicker(pdf, onPickFile, { pickScan = true }, tvm::clearInput)
            if (pdf != null) {
                Text(
                    "Toca páginas para seleccionarlas. Con una sola seleccionada puedes moverla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                        val isSel = item.id in selected
                        val shape = RoundedCornerShape(6.dp)
                        Box(
                            Modifier
                                .clip(shape)
                                .border(
                                    if (isSel) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                                    else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    shape,
                                )
                                .clickable { selected = if (isSel) selected - item.id else selected + item.id }
                                .background(MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            PdfPageImage(
                                file = pdf.file,
                                index = item.op.source,
                                widthPx = 300,
                                modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).padding(4.dp).rotate(item.op.rotation.toFloat()),
                            )
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(4.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                            if (isSel) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (pickScan) {
        ScanPickerDialog(scans, multiple = false, onDismiss = { pickScan = false }, onPick = { tvm.pickScans(it, InputTarget.SINGLE) })
    }
}
