package io.github.littledumbduck.pdfscan.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.littledumbduck.pdfscan.data.ScanDocument
import io.github.littledumbduck.pdfscan.tools.PdfFile

/** Tarjeta que muestra el PDF de entrada o los botones para elegirlo. */
@Composable
fun InputPicker(
    input: PdfFile?,
    onPickFile: () -> Unit,
    onPickScan: () -> Unit,
    onClear: () -> Unit,
) {
    if (input == null) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Elige el PDF", style = MaterialTheme.typography.titleMedium)
            FilledTonalButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.FolderOpen, null); Text("  PDF del móvil")
            }
            OutlinedButton(onClick = onPickScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DocumentScanner, null); Text("  De mis escaneos")
            }
        }
    } else {
        Card(
            Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        ) {
            Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PictureAsPdf, null)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(input.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${input.pages} página(s)", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onClear) { Icon(Icons.Default.Close, "Cambiar PDF") }
            }
        }
    }
}

/** Diálogo para elegir documentos de la biblioteca de escaneos. */
@Composable
fun ScanPickerDialog(
    docs: List<ScanDocument>,
    multiple: Boolean,
    onDismiss: () -> Unit,
    onPick: (List<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(listOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (multiple) "Elige escaneos" else "Elige un escaneo") },
        text = {
            if (docs.isEmpty()) {
                Text("Todavía no tienes escaneos.")
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(docs, key = { it.id }) { doc ->
                        val isSel = doc.id in selected
                        ListItem(
                            headlineContent = { Text(doc.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${doc.pages.size} pág") },
                            leadingContent = if (multiple) {
                                { Icon(if (isSel) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank, null) }
                            } else null,
                            modifier = Modifier.clickable {
                                if (multiple) selected = if (isSel) selected - doc.id else selected + doc.id
                                else { onPick(listOf(doc.id)); onDismiss() }
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (multiple) {
                TextButton(enabled = selected.isNotEmpty(), onClick = { onPick(selected); onDismiss() }) {
                    Text("Añadir (${selected.size})")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
