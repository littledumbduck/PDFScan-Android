package io.github.littledumbduck.pdfscan.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.littledumbduck.pdfscan.tools.PdfPageImage
import io.github.littledumbduck.pdfscan.tools.PdfPreview
import io.github.littledumbduck.pdfscan.tools.ToolKind
import io.github.littledumbduck.pdfscan.tools.ToolsViewModel
import java.io.File
import kotlin.math.min

/** Visor de PDF con acceso directo a las herramientas. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(
    tvm: ToolsViewModel,
    file: File,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSaveFile: (File) -> Unit,
    onTool: (ToolKind) -> Unit,
) {
    var toolsMenu by remember { mutableStateOf(false) }
    val sizes by produceState<List<Pair<Int, Int>>?>(null, file) {
        value = runCatching { PdfPreview.pageSizes(file) }.getOrDefault(emptyList())
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = {
                    Text(file.nameWithoutExtension, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                actions = {
                    IconButton(onClick = { tvm.shareFiles(listOf(file)) }) { Icon(Icons.Default.Share, "Compartir") }
                    IconButton(onClick = { onSaveFile(file) }) { Icon(Icons.Default.Save, "Guardar en…") }
                    IconButton(onClick = { toolsMenu = true }) { Icon(Icons.Default.Build, "Herramientas") }
                    DropdownMenu(expanded = toolsMenu, onDismissRequest = { toolsMenu = false }) {
                        ToolKind.entries.filter { it != ToolKind.MERGE && it != ToolKind.IMAGES_TO_PDF }.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.label) },
                                leadingIcon = { Icon(t.icon, null) },
                                onClick = { toolsMenu = false; onTool(t) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Unir con otros PDFs") },
                            leadingIcon = { Icon(ToolKind.MERGE.icon, null) },
                            onClick = { toolsMenu = false; onTool(ToolKind.MERGE) },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Añadir a Escaneos (OCR, editar)") },
                            leadingIcon = { Icon(Icons.Default.DocumentScanner, null) },
                            onClick = { toolsMenu = false; tvm.addToScans(listOf(file)) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
            val widthPx = min(constraints.maxWidth, 1600)
            val pages = sizes
            when {
                pages == null -> Unit
                pages.isEmpty() -> Text("No se puede mostrar este PDF.", Modifier.padding(24.dp))
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(pages.size) { i ->
                        val (w, h) = pages[i]
                        PdfPageImage(
                            file = file,
                            index = i,
                            widthPx = widthPx,
                            modifier = Modifier.fillMaxWidth().aspectRatio(w.toFloat() / h.coerceAtLeast(1)),
                        )
                    }
                }
            }
        }
    }
}
