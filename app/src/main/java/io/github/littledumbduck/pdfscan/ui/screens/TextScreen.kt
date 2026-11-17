package io.github.littledumbduck.pdfscan.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.ui.AppViewModel
import kotlinx.coroutines.launch

@Suppress("DEPRECATION") // LocalClipboardManager: API sencilla y suficiente aquí
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextScreen(vm: AppViewModel, docId: String, snackbar: SnackbarHostState, onBack: () -> Unit) {
    val docs by vm.allDocuments.collectAsStateWithLifecycle()
    val ocrRunning by vm.ocrRunning.collectAsStateWithLifecycle()
    val doc = docs.firstOrNull { it.id == docId }
    LaunchedEffect(doc == null) { if (doc == null) onBack() }
    if (doc == null) return

    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val text = doc.pages.mapIndexedNotNull { i, p ->
        p.ocr?.text?.takeIf { it.isNotBlank() }?.let { if (doc.pages.size > 1) "— Página ${i + 1} —\n$it" else it }
    }.joinToString("\n\n")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text("Texto") },
                actions = {
                    if (text.isNotBlank()) {
                        IconButton(onClick = {
                            clipboard.setText(AnnotatedString(doc.fullText))
                            scope.launch { snackbar.showSnackbar("Texto copiado") }
                        }) { Icon(Icons.Default.ContentCopy, "Copiar todo") }
                        IconButton(onClick = { vm.shareText(doc.id) }) { Icon(Icons.Default.Share, "Compartir texto") }
                    }
                },
            )
        },
    ) { padding ->
        when {
            docId in ocrRunning -> Column(
                Modifier.padding(padding).fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("Reconociendo texto…", Modifier.padding(16.dp))
            }

            text.isBlank() -> Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (doc.hasOcr) "No se ha encontrado texto en este documento."
                    else "Aún no se ha reconocido el texto de este documento.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (!doc.hasOcr) {
                    Button(onClick = { vm.runOcr(doc.id) }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Reconocer texto (OCR)")
                    }
                }
            }

            else -> SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }
}
