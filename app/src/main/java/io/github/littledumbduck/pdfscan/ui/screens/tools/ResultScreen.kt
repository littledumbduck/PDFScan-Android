package io.github.littledumbduck.pdfscan.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.tools.ToolsViewModel
import kotlinx.coroutines.launch
import java.io.File

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    tvm: ToolsViewModel,
    snackbar: SnackbarHostState,
    onClose: () -> Unit,
    onOpenFile: (File) -> Unit,
    onSaveFile: (File) -> Unit,
    onContinue: (File) -> Unit,
) {
    val result by tvm.result.collectAsStateWithLifecycle()
    val r = result
    LaunchedEffect(r == null) { if (r == null) onClose() }
    if (r == null) return
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Cerrar") } },
                title = { Text(r.title) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                Column(Modifier.padding(start = 12.dp)) {
                    Text(r.title, style = MaterialTheme.typography.titleLarge)
                    r.details?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            if (r.text != null) {
                if (r.text.isNotEmpty()) {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = {
                            clipboard.setText(AnnotatedString(r.text))
                            scope.launch { snackbar.showSnackbar("Texto copiado") }
                        }) { Icon(Icons.Default.ContentCopy, null); Text("  Copiar") }
                        FilledTonalButton(onClick = { tvm.shareText(r.text) }) { Icon(Icons.Default.Share, null); Text("  Compartir") }
                    }
                    LazyColumn(Modifier.weight(1f).padding(16.dp)) {
                        item { SelectionContainer { Text(r.text, style = MaterialTheme.typography.bodyMedium) } }
                    }
                }
                return@Column
            }

            if (r.files.size > 1) {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { tvm.shareFiles(r.files) }) { Icon(Icons.Default.Share, null); Text("  Compartir todo") }
                    if (r.images) {
                        OutlinedButton(onClick = { tvm.addToScans(r.files) }) {
                            Icon(Icons.Default.DocumentScanner, null); Text("  A Escaneos")
                        }
                    }
                }
            }

            LazyColumn(Modifier.weight(1f)) {
                items(r.files, key = { it.absolutePath }) { f ->
                    val isPdf = f.extension.equals("pdf", true)
                    Column {
                        ListItem(
                            leadingContent = {
                                Icon(if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image, null, tint = MaterialTheme.colorScheme.error)
                            },
                            headlineContent = { Text(f.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(ToolsViewModel.size(f.length())) },
                            modifier = if (isPdf) Modifier.clickable { onOpenFile(f) } else Modifier,
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = { tvm.shareFiles(listOf(f)) }) { Icon(Icons.Default.Share, null); Text(" Compartir") }
                            OutlinedButton(onClick = { onSaveFile(f) }) { Icon(Icons.Default.Save, null); Text(" Guardar") }
                            if (isPdf) {
                                OutlinedButton(onClick = { onContinue(f) }) { Icon(Icons.Default.Build, null); Text(" Más") }
                            }
                        }
                    }
                }
            }
        }
    }
}
