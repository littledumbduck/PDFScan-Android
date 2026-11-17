package io.github.littledumbduck.pdfscan.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.github.littledumbduck.pdfscan.tools.ToolKind
import io.github.littledumbduck.pdfscan.tools.ToolsViewModel
import io.github.littledumbduck.pdfscan.ui.ConfirmDialog
import io.github.littledumbduck.pdfscan.ui.TextInputDialog
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsHomeScreen(
    tvm: ToolsViewModel,
    snackbar: SnackbarHostState,
    bottomBar: @Composable () -> Unit,
    onTool: (ToolKind) -> Unit,
    onOpenFile: (File) -> Unit,
    onSaveFile: (File) -> Unit,
) {
    val outputs by tvm.outputs.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<File?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text("Herramientas PDF") }) },
        bottomBar = bottomBar,
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding),
        ) {
            items(ToolKind.entries) { tool ->
                Card(
                    onClick = { onTool(tool) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(14.dp).fillMaxWidth()) {
                        Icon(tool.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text(tool.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                        Text(
                            tool.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2,
                            maxLines = 2,
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Mis archivos PDF",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                )
            }
            if (outputs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "Aquí aparecerán los PDFs que crees con las herramientas.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(4.dp),
                    )
                }
            }
            items(outputs, key = { it.absolutePath }, span = { GridItemSpan(maxLineSpan) }) { file ->
                var menu by remember { mutableStateOf(false) }
                ListItem(
                    leadingContent = { Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text(file.nameWithoutExtension, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Text(
                            "${ToolsViewModel.size(file.length())} · " +
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(file.lastModified())),
                        )
                    },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Compartir") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = { menu = false; tvm.shareFiles(listOf(file)) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Guardar en…") },
                                    leadingIcon = { Icon(Icons.Default.Save, null) },
                                    onClick = { menu = false; onSaveFile(file) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Renombrar") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menu = false; renaming = file },
                                )
                                DropdownMenuItem(
                                    text = { Text("Eliminar") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { menu = false; deleting = file },
                                )
                            }
                        }
                    },
                    modifier = Modifier.clickable { onOpenFile(file) },
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.height(24.dp)) }
        }
    }

    renaming?.let { f ->
        TextInputDialog(
            title = "Renombrar",
            initial = f.nameWithoutExtension,
            label = "Nombre",
            onDismiss = { renaming = null },
            onConfirm = { tvm.renameOutput(f, it); renaming = null },
        )
    }
    deleting?.let { f ->
        ConfirmDialog(
            title = "Eliminar archivo",
            text = "\"${f.nameWithoutExtension}\" se eliminará de forma permanente.",
            confirmLabel = "Eliminar",
            onDismiss = { deleting = null },
            onConfirm = { tvm.deleteOutput(f) },
        )
    }
}
