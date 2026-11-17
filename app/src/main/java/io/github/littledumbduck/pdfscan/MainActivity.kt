package io.github.littledumbduck.pdfscan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.github.littledumbduck.pdfscan.ui.AppViewModel
import io.github.littledumbduck.pdfscan.ui.BusyOverlay
import io.github.littledumbduck.pdfscan.ui.screens.DocumentScreen
import io.github.littledumbduck.pdfscan.ui.screens.LibraryScreen
import io.github.littledumbduck.pdfscan.ui.screens.PageScreen
import io.github.littledumbduck.pdfscan.ui.screens.SettingsScreen
import io.github.littledumbduck.pdfscan.ui.screens.TextScreen
import io.github.littledumbduck.pdfscan.ui.theme.PdfScanTheme
import io.github.littledumbduck.pdfscan.util.ShareUtils

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    // Escáner de documentos de Google (ML Kit): detección de bordes, recorte, filtros,
    // limpieza de sombras/manchas/dedos e importación desde galería. Todo en el dispositivo.
    private val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .build()

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        if (res.resultCode == RESULT_OK) {
            val result = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            vm.onScanned(result?.pages?.map { it.imageUri }.orEmpty())
        } else {
            vm.scanTarget = null
        }
    }

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        vm.importImages(uris)
    }

    private val pickPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importPdf)
    }

    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) vm.writeExport(uri) else vm.pendingExport = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PdfScanTheme { AppRoot() } }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun startScan(targetDocId: String? = null) {
        vm.scanTarget = targetDocId
        GmsDocumentScanning.getClient(scannerOptions)
            .getStartScanIntent(this)
            .addOnSuccessListener { sender -> scanLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            .addOnFailureListener { e ->
                vm.scanTarget = null
                Toast.makeText(
                    this,
                    "No se pudo abrir el escáner (¿Google Play Services actualizado?): ${e.message}",
                    Toast.LENGTH_LONG,
                ).show()
            }
    }

    private fun startExport(docId: String, password: String?) {
        val doc = vm.allDocuments.value.firstOrNull { it.id == docId } ?: return
        vm.pendingExport = AppViewModel.PendingExport(docId, password)
        createPdf.launch("${ShareUtils.safeFileName(doc.name)}.pdf")
    }

    private fun handleIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_SCAN -> startScan()
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { importShared(listOf(it), intent.type) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?.let { importShared(it, intent.type) }
            }
        }
    }

    private fun importShared(uris: List<Uri>, intentType: String?) {
        val (pdfs, images) = uris.partition { uri ->
            val type = contentResolver.getType(uri) ?: intentType
            type == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)
        }
        pdfs.forEach(vm::importPdf)
        if (images.isNotEmpty()) vm.importImages(images)
    }

    @Composable
    private fun AppRoot() {
        val snackbar = remember { SnackbarHostState() }
        val busy by vm.busy.collectAsStateWithLifecycle()
        val stack: SnapshotStateList<String> = rememberSaveable(
            saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
        ) { mutableStateListOf("library") }

        fun push(route: String) = stack.add(route)
        fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }

        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(Unit) {
            vm.openDocument.collect { id ->
                // Sustituye cualquier pantalla abierta por el documento nuevo.
                while (stack.size > 1) stack.removeAt(stack.lastIndex)
                push("doc/$id")
            }
        }
        BackHandler(enabled = stack.size > 1) { pop() }

        val route = stack.last()
        val parts = route.split("/")
        when (parts[0]) {
            "doc" -> DocumentScreen(
                vm = vm,
                docId = parts[1],
                snackbar = snackbar,
                onBack = ::pop,
                onOpenPage = { push("page/${parts[1]}/$it") },
                onAddPages = { startScan(parts[1]) },
                onExport = { pwd -> startExport(parts[1], pwd) },
                onShowText = { push("text/${parts[1]}") },
            )

            "page" -> PageScreen(vm, parts[1], parts[2].toInt(), snackbar, onBack = ::pop)
            "text" -> TextScreen(vm, parts[1], snackbar, onBack = ::pop)
            "settings" -> SettingsScreen(vm, onBack = ::pop)
            else -> LibraryScreen(
                vm = vm,
                snackbar = snackbar,
                onOpen = { push("doc/$it") },
                onScan = { startScan() },
                onImportImages = {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImportPdf = { pickPdf.launch(arrayOf("application/pdf")) },
                onSettings = { push("settings") },
            )
        }

        busy?.let { BusyOverlay(it) }
    }

    companion object {
        const val ACTION_SCAN = "io.github.littledumbduck.pdfscan.ACTION_SCAN"
    }
}

private fun <T> List<T>.toMutableStateList(): SnapshotStateList<T> = mutableStateListOf<T>().also { it.addAll(this) }
