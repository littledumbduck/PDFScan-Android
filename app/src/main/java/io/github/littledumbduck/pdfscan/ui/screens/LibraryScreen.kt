package io.github.littledumbduck.pdfscan.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.data.ScanDocument
import io.github.littledumbduck.pdfscan.ui.AppViewModel
import io.github.littledumbduck.pdfscan.ui.ConfirmDialog
import io.github.littledumbduck.pdfscan.ui.FileImage
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    vm: AppViewModel,
    snackbar: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
    onOpen: (String) -> Unit,
    onScan: () -> Unit,
    onImportImages: () -> Unit,
    onImportPdf: () -> Unit,
    onSettings: () -> Unit,
) {
    val docs by vm.documents.collectAsStateWithLifecycle()
    val all by vm.allDocuments.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val ocrRunning by vm.ocrRunning.collectAsStateWithLifecycle()

    var searching by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // Orden de selección = orden de unión
    var selection by remember { mutableStateOf(listOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()

    BackHandler(enabled = selecting || searching) {
        if (selecting) selection = emptyList() else { searching = false; vm.setQuery("") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (!selecting) bottomBar() },
        topBar = {
            when {
                selecting -> TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { selection = emptyList() }) { Icon(Icons.Default.Close, "Cancelar") }
                    },
                    title = { Text("${selection.size} seleccionados") },
                    actions = {
                        if (selection.size >= 2) {
                            IconButton(onClick = { vm.merge(selection); selection = emptyList() }) {
                                Icon(Icons.Default.Layers, "Unir en un PDF")
                            }
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar") }
                    },
                )

                searching -> TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { searching = false; vm.setQuery("") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Cerrar búsqueda")
                        }
                    },
                    title = {
                        TextField(
                            value = query,
                            onValueChange = vm::setQuery,
                            placeholder = { Text("Buscar por nombre o texto") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    actions = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Default.Close, "Borrar") }
                    },
                )

                else -> TopAppBar(
                    title = { Text("PDFScan") },
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Default.Search, "Buscar") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Más") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Importar imágenes") },
                                leadingIcon = { Icon(Icons.Default.PhotoLibrary, null) },
                                onClick = { menu = false; onImportImages() },
                            )
                            DropdownMenuItem(
                                text = { Text("Importar PDF") },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                                onClick = { menu = false; onImportPdf() },
                            )
                            DropdownMenuItem(
                                text = { Text("Ajustes") },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                                onClick = { menu = false; onSettings() },
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!selecting) {
                ExtendedFloatingActionButton(
                    onClick = onScan,
                    icon = { Icon(Icons.Default.DocumentScanner, null) },
                    text = { Text("Escanear") },
                )
            }
        },
    ) { padding ->
        if (all.isEmpty()) {
            EmptyLibrary(Modifier.padding(padding), onScan, onImportImages, onImportPdf)
        } else if (docs.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sin resultados para \"$query\"", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding),
            ) {
                items(docs, key = { it.id }) { doc ->
                    val selected = doc.id in selection
                    DocumentCard(
                        vm = vm,
                        doc = doc,
                        selected = selected,
                        ocrRunning = doc.id in ocrRunning,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                if (selecting) selection = if (selected) selection - doc.id else selection + doc.id
                                else onOpen(doc.id)
                            },
                            onLongClick = { selection = if (selected) selection - doc.id else selection + doc.id },
                        ),
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Eliminar documentos",
            text = "Se eliminarán ${selection.size} documento(s) de forma permanente.",
            confirmLabel = "Eliminar",
            onDismiss = { confirmDelete = false },
            onConfirm = { vm.delete(selection); selection = emptyList() },
        )
    }
}

@Composable
private fun DocumentCard(
    vm: AppViewModel,
    doc: ScanDocument,
    selected: Boolean,
    ocrRunning: Boolean,
    modifier: Modifier,
) {
    Card(
        modifier = modifier,
        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box {
            val first = doc.pages.firstOrNull()
            if (first != null) {
                FileImage(
                    file = vm.repoPageFile(doc, first.file),
                    maxPx = 400,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.75f),
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp),
                )
            }
        }
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${doc.pages.size} pág · ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(doc.updatedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (doc.hasOcr || ocrRunning) {
                    Icon(
                        Icons.Default.TextFields,
                        contentDescription = if (ocrRunning) "Reconociendo texto" else "Texto reconocido",
                        tint = if (ocrRunning) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier, onScan: () -> Unit, onImportImages: () -> Unit, onImportPdf: () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.DocumentScanner, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Aún no hay documentos", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Escanea con la cámara o importa imágenes y PDFs. Todo se procesa en tu móvil.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onScan) { Text("Escanear documento") }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onImportImages) { Text("Imágenes") }
            FilledTonalButton(onClick = onImportPdf) { Text("PDF") }
        }
    }
}
