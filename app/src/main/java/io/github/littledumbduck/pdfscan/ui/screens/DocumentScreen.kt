package io.github.littledumbduck.pdfscan.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.ui.AppViewModel
import io.github.littledumbduck.pdfscan.ui.ConfirmDialog
import io.github.littledumbduck.pdfscan.ui.FileImage
import io.github.littledumbduck.pdfscan.ui.TextInputDialog

private enum class DocDialog { NONE, RENAME, DELETE_DOC, DELETE_PAGES, SHARE_PASSWORD, EXPORT_PASSWORD }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DocumentScreen(
    vm: AppViewModel,
    docId: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onOpenPage: (Int) -> Unit,
    onAddPages: () -> Unit,
    onExport: (password: String?) -> Unit,
    onShowText: () -> Unit,
) {
    val docs by vm.allDocuments.collectAsStateWithLifecycle()
    val ocrRunning by vm.ocrRunning.collectAsStateWithLifecycle()
    val doc = docs.firstOrNull { it.id == docId }

    // El documento se ha eliminado (o aún no está cargado): volver.
    LaunchedEffect(doc == null) { if (doc == null) onBack() }
    if (doc == null) return

    var selection by remember { mutableStateOf(setOf<String>()) }
    var dialog by remember { mutableStateOf(DocDialog.NONE) }
    var shareMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val selecting = selection.isNotEmpty()
    // Ignora páginas seleccionadas que ya no existen.
    val validSelection = selection.intersect(doc.pages.map { it.file }.toSet())

    BackHandler(enabled = selecting) { selection = emptySet() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
                if (selecting) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { selection = emptySet() }) { Icon(Icons.Default.Close, "Cancelar") }
                        },
                        title = { Text("${validSelection.size} páginas") },
                        actions = {
                            IconButton(onClick = {
                                validSelection.forEach { vm.rotatePage(doc.id, it) }
                            }) { Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotar") }
                            IconButton(onClick = {
                                vm.extract(doc.id, validSelection); selection = emptySet()
                            }) { Icon(Icons.Default.ContentCut, "Extraer a documento nuevo") }
                            IconButton(onClick = { dialog = DocDialog.DELETE_PAGES }) { Icon(Icons.Default.Delete, "Eliminar páginas") }
                        },
                    )
                } else {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") }
                        },
                        title = {
                            Text(
                                doc.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { dialog = DocDialog.RENAME },
                            )
                        },
                        actions = {
                            IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, "Compartir") }
                            DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("PDF") },
                                    leadingIcon = { Icon(Icons.Default.PictureAsPdf, null) },
                                    onClick = { shareMenu = false; vm.sharePdf(doc.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text("PDF con contraseña") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    onClick = { shareMenu = false; dialog = DocDialog.SHARE_PASSWORD },
                                )
                                DropdownMenuItem(
                                    text = { Text("Imágenes (JPG)") },
                                    leadingIcon = { Icon(Icons.Default.Image, null) },
                                    onClick = { shareMenu = false; vm.shareImages(doc.id) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Texto (OCR)") },
                                    leadingIcon = { Icon(Icons.Default.TextFields, null) },
                                    onClick = { shareMenu = false; vm.shareText(doc.id) },
                                )
                            }
                            IconButton(onClick = { moreMenu = true }) { Icon(Icons.Default.MoreVert, "Más") }
                            DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Renombrar") },
                                    leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { moreMenu = false; dialog = DocDialog.RENAME },
                                )
                                DropdownMenuItem(
                                    text = { Text("Guardar PDF en…") },
                                    leadingIcon = { Icon(Icons.Default.Save, null) },
                                    onClick = { moreMenu = false; onExport(null) },
                                )
                                DropdownMenuItem(
                                    text = { Text("Guardar PDF con contraseña…") },
                                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                                    onClick = { moreMenu = false; dialog = DocDialog.EXPORT_PASSWORD },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Ver / copiar texto") },
                                    leadingIcon = { Icon(Icons.Default.TextFields, null) },
                                    onClick = { moreMenu = false; onShowText() },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (doc.hasOcr) "Texto ya reconocido" else "Reconocer texto (OCR)") },
                                    enabled = !doc.hasOcr && docId !in ocrRunning,
                                    leadingIcon = { Icon(Icons.Default.TextFields, null) },
                                    onClick = { moreMenu = false; vm.runOcr(doc.id) },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Eliminar documento") },
                                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { moreMenu = false; dialog = DocDialog.DELETE_DOC },
                                )
                            }
                        },
                    )
                }
                if (docId in ocrRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        bottomBar = {
            if (!selecting) {
                BottomAppBar {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = onAddPages, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.AddAPhoto, null)
                            Text("  Añadir", maxLines = 1)
                        }
                        Button(onClick = { vm.sharePdf(doc.id) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Share, null)
                            Text("  Compartir PDF", maxLines = 1)
                        }
                    }
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            itemsIndexed(doc.pages, key = { _, p -> p.file }) { index, page ->
                val selected = page.file in validSelection
                val shape = RoundedCornerShape(8.dp)
                Box(
                    Modifier
                        .clip(shape)
                        .border(
                            if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
                            else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape,
                        )
                        .combinedClickable(
                            onClick = {
                                if (selecting) selection = if (selected) selection - page.file else selection + page.file
                                else onOpenPage(index)
                            },
                            onLongClick = { selection = if (selected) selection - page.file else selection + page.file },
                        ),
                ) {
                    FileImage(
                        file = vm.repoPageFile(doc, page.file),
                        maxPx = 500,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.72f)
                            .background(MaterialTheme.colorScheme.surfaceContainer),
                    )
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(6.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                    if (selected) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp),
                        )
                    }
                }
            }
        }
    }

    when (dialog) {
        DocDialog.RENAME -> TextInputDialog(
            title = "Renombrar",
            initial = doc.name,
            label = "Nombre",
            extraAction = if (doc.hasOcr) "Sugerir a partir del texto" to { vm.suggestedName(doc) } else null,
            onDismiss = { dialog = DocDialog.NONE },
            onConfirm = { vm.rename(doc.id, it); dialog = DocDialog.NONE },
        )

        DocDialog.DELETE_DOC -> ConfirmDialog(
            title = "Eliminar documento",
            text = "\"${doc.name}\" se eliminará de forma permanente.",
            confirmLabel = "Eliminar",
            onDismiss = { dialog = DocDialog.NONE },
            onConfirm = { vm.delete(listOf(doc.id)) },
        )

        DocDialog.DELETE_PAGES -> ConfirmDialog(
            title = "Eliminar páginas",
            text = "Se eliminarán ${validSelection.size} página(s).",
            confirmLabel = "Eliminar",
            onDismiss = { dialog = DocDialog.NONE },
            onConfirm = { vm.deletePages(doc.id, validSelection); selection = emptySet() },
        )

        DocDialog.SHARE_PASSWORD, DocDialog.EXPORT_PASSWORD -> {
            val share = dialog == DocDialog.SHARE_PASSWORD
            TextInputDialog(
                title = "Proteger con contraseña",
                initial = "",
                label = "Contraseña (cifrado AES-256)",
                password = true,
                confirmLabel = if (share) "Compartir" else "Guardar",
                onDismiss = { dialog = DocDialog.NONE },
                onConfirm = { pwd ->
                    dialog = DocDialog.NONE
                    if (share) vm.sharePdf(doc.id, pwd) else onExport(pwd)
                },
            )
        }

        DocDialog.NONE -> Unit
    }
}
