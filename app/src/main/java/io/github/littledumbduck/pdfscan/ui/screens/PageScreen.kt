package io.github.littledumbduck.pdfscan.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.ui.AppViewModel
import io.github.littledumbduck.pdfscan.ui.ConfirmDialog
import io.github.littledumbduck.pdfscan.ui.FileImage
import io.github.littledumbduck.pdfscan.util.PageFilter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageScreen(
    vm: AppViewModel,
    docId: String,
    initialIndex: Int,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val docs by vm.allDocuments.collectAsStateWithLifecycle()
    val doc = docs.firstOrNull { it.id == docId }
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val gone = doc == null || doc.pages.isEmpty()
    LaunchedEffect(loaded, gone) { if (loaded && gone) onBack() }
    if (doc == null || doc.pages.isEmpty()) return

    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, doc.pages.lastIndex)) { doc.pages.size }
    val scope = rememberCoroutineScope()
    val current = doc.pages.getOrNull(pager.currentPage) ?: doc.pages.last()
    var filterMenu by remember { mutableStateOf(false) }
    // Página que se acaba de mover: cuando llegue la lista reordenada, el visor salta a su nueva posición.
    var follow by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(doc.pages) {
        follow?.let { f ->
            val i = doc.pages.indexOfFirst { it.file == f }
            if (i >= 0) pager.scrollToPage(i)
            follow = null
        }
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text("Página ${pager.currentPage + 1} de ${doc.pages.size}") },
            )
        },
        bottomBar = {
            BottomAppBar {
                IconButton(
                    onClick = {
                        follow = current.file
                        vm.movePage(doc.id, current.file, -1)
                    },
                    enabled = pager.currentPage > 0,
                ) { Icon(Icons.Default.ChevronLeft, "Mover antes") }
                IconButton(onClick = { vm.rotatePage(doc.id, current.file) }) { Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotar") }
                Box {
                    IconButton(onClick = { filterMenu = true }) { Icon(Icons.Default.FilterBAndW, "Filtros") }
                    DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                        PageFilter.entries.forEach { f ->
                            DropdownMenuItem(
                                text = { Text(f.label) },
                                onClick = { filterMenu = false; vm.applyFilter(doc.id, current.file, f) },
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { vm.restoreOriginal(doc.id, current.file) },
                    enabled = current.original != null,
                ) { Icon(Icons.Default.Restore, "Restaurar original") }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar página") }
                IconButton(
                    onClick = {
                        follow = current.file
                        vm.movePage(doc.id, current.file, 1)
                    },
                    enabled = pager.currentPage < doc.pages.lastIndex,
                ) { Icon(Icons.Default.ChevronRight, "Mover después") }
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pager,
            key = { doc.pages.getOrNull(it)?.file ?: it },
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest),
        ) { index ->
            val page = doc.pages.getOrNull(index) ?: return@HorizontalPager
            ZoomableImage { modifier ->
                FileImage(file = vm.repoPageFile(doc, page.file), maxPx = 2400, modifier = modifier)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Eliminar página",
            text = "Se eliminará la página ${pager.currentPage + 1}.",
            confirmLabel = "Eliminar",
            onDismiss = { confirmDelete = false },
            onConfirm = { vm.deletePages(doc.id, setOf(current.file)) },
        )
    }
}

/** Zoom con pellizco y doble toque. Con zoom 1x el deslizamiento horizontal pasa al pager. */
@Composable
private fun ZoomableImage(content: @Composable (Modifier) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan * scale
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .transformable(state, canPan = { scale > 1f }),
    ) {
        content(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer {
                    scaleX = scale; scaleY = scale
                    translationX = offset.x; translationY = offset.y
                },
        )
    }
}
