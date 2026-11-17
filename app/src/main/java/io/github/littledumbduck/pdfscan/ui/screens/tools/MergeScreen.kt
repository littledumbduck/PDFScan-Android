package io.github.littledumbduck.pdfscan.ui.screens.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.data.ScanDocument
import io.github.littledumbduck.pdfscan.tools.InputTarget
import io.github.littledumbduck.pdfscan.tools.ToolsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(
    tvm: ToolsViewModel,
    scans: List<ScanDocument>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPickFiles: () -> Unit,
) {
    val list by tvm.mergeList.collectAsStateWithLifecycle()
    var pickScans by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text("Unir PDFs") },
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    onClick = tvm::merge,
                    enabled = list.size >= 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                ) { Text(if (list.size >= 2) "Unir ${list.size} archivos" else "Añade al menos 2 archivos") }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onPickFiles, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FolderOpen, null); Text("  PDFs del móvil", maxLines = 1)
                }
                FilledTonalButton(onClick = { pickScans = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.DocumentScanner, null); Text("  Escaneos", maxLines = 1)
                }
            }
            if (list.isEmpty()) {
                Text(
                    "Añade PDFs del móvil y/o documentos escaneados. Se unirán en el orden de la lista.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(list, key = { _, p -> p.file.absolutePath }) { i, pdf ->
                    ListItem(
                        leadingContent = { Text("${i + 1}", style = MaterialTheme.typography.titleMedium) },
                        headlineContent = { Text(pdf.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text("${pdf.pages} página(s)") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { tvm.moveInMerge(i, -1) }, enabled = i > 0) {
                                    Icon(Icons.Default.ArrowUpward, "Subir")
                                }
                                IconButton(onClick = { tvm.moveInMerge(i, 1) }, enabled = i < list.lastIndex) {
                                    Icon(Icons.Default.ArrowDownward, "Bajar")
                                }
                                IconButton(onClick = { tvm.removeFromMerge(i) }) { Icon(Icons.Default.Close, "Quitar") }
                            }
                        },
                    )
                }
            }
        }
    }

    if (pickScans) {
        ScanPickerDialog(
            docs = scans,
            multiple = true,
            onDismiss = { pickScans = false },
            onPick = { ids -> tvm.pickScans(ids, InputTarget.MERGE) },
        )
    }
}
