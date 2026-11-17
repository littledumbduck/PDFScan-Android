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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import io.github.littledumbduck.pdfscan.tools.InputTarget
import io.github.littledumbduck.pdfscan.tools.ToolKind
import io.github.littledumbduck.pdfscan.tools.ToolsViewModel
import io.github.littledumbduck.pdfscan.ui.AppViewModel
import io.github.littledumbduck.pdfscan.ui.BusyOverlay
import io.github.littledumbduck.pdfscan.ui.TextInputDialog
import io.github.littledumbduck.pdfscan.ui.screens.DocumentScreen
import io.github.littledumbduck.pdfscan.ui.screens.LibraryScreen
import io.github.littledumbduck.pdfscan.ui.screens.PageScreen
import io.github.littledumbduck.pdfscan.ui.screens.SettingsScreen
import io.github.littledumbduck.pdfscan.ui.screens.TextScreen
import io.github.littledumbduck.pdfscan.ui.screens.tools.MergeScreen
import io.github.littledumbduck.pdfscan.ui.screens.tools.OrganizeScreen
import io.github.littledumbduck.pdfscan.ui.screens.tools.ResultScreen
import io.github.littledumbduck.pdfscan.ui.screens.tools.SignScreen
import io.github.littledumbduck.pdfscan.ui.screens.tools.SimpleToolScreen
import io.github.littledumbduck.pdfscan.ui.screens.tools.ToolsHomeScreen
import io.github.littledumbduck.pdfscan.ui.screens.tools.ViewerScreen
import io.github.littledumbduck.pdfscan.ui.theme.PdfScanTheme
import io.github.littledumbduck.pdfscan.util.ShareUtils
import java.io.File

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()
    private val tvm: ToolsViewModel by viewModels()

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

    // --- Herramientas PDF
    private val pickToolPdf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { tvm.pickUris(listOf(it), InputTarget.SINGLE) }
    }

    private val pickMergePdfs = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) tvm.pickUris(uris, InputTarget.MERGE)
    }

    private val saveToolPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) tvm.writeSave(uri) else tvm.pendingSave = null
    }

    private val saveToolJpg = registerForActivityResult(ActivityResultContracts.CreateDocument("image/jpeg")) { uri ->
        if (uri != null) tvm.writeSave(uri) else tvm.pendingSave = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PdfScanTheme { AppRoot() } }
        // Al recrearse (rotación) el intent ya está marcado y no se repite; si es nuevo, se procesa.
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
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

    private fun saveFile(file: File) {
        tvm.pendingSave = file
        if (file.extension.equals("pdf", true)) saveToolPdf.launch(file.name) else saveToolJpg.launch(file.name)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.getBooleanExtra(EXTRA_HANDLED, false)) return
        intent.putExtra(EXTRA_HANDLED, true)
        when (intent.action) {
            ACTION_SCAN -> startScan()
            Intent.ACTION_VIEW -> intent.data?.let(tvm::openExternal)
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
        val toolBusy by tvm.busy.collectAsStateWithLifecycle()
        val passwordRequest by tvm.passwordRequest.collectAsStateWithLifecycle()
        val scans by vm.allDocuments.collectAsStateWithLifecycle()
        val stack: SnapshotStateList<String> = rememberSaveable(
            saver = listSaver(save = { it.toList() }, restore = { it.toMutableStateList() }),
        ) { mutableStateListOf(ROUTE_LIBRARY) }

        fun push(route: String) = stack.add(route)
        fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
        fun popToRoot() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }
        fun openTool(kind: ToolKind, fromHome: Boolean) {
            when (kind) {
                ToolKind.IMAGES_TO_PDF -> pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                else -> {
                    if (fromHome && kind != ToolKind.MERGE) tvm.clearInput()
                    push("tool/${kind.name}")
                }
            }
        }

        LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(Unit) { tvm.messages.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(Unit) {
            vm.openDocument.collect { id ->
                // Abre el documento nuevo en la pestaña de escaneos.
                stack.clear(); stack.add(ROUTE_LIBRARY); push("doc/$id")
            }
        }
        LaunchedEffect(Unit) { tvm.navigate.collect { push(it) } }
        BackHandler(enabled = stack.size > 1) { pop() }

        val bottomBar = @Composable {
            NavigationBar {
                NavigationBarItem(
                    selected = stack.first() == ROUTE_LIBRARY,
                    onClick = { stack.clear(); stack.add(ROUTE_LIBRARY) },
                    icon = { Icon(Icons.Default.DocumentScanner, null) },
                    label = { Text("Escaneos") },
                )
                NavigationBarItem(
                    selected = stack.first() == ROUTE_TOOLS,
                    onClick = { stack.clear(); stack.add(ROUTE_TOOLS) },
                    icon = { Icon(Icons.Default.Build, null) },
                    label = { Text("Herramientas PDF") },
                )
            }
        }

        val route = stack.last()
        val parts = route.split("/", limit = 2)
        val arg = parts.getOrNull(1).orEmpty()
        when (parts[0]) {
            "doc" -> DocumentScreen(
                vm = vm,
                docId = arg,
                snackbar = snackbar,
                onBack = ::pop,
                onOpenPage = { push("page/$arg/$it") },
                onAddPages = { startScan(arg) },
                onExport = { pwd -> startExport(arg, pwd) },
                onShowText = { push("text/$arg") },
                onOpenInTools = { tvm.openScanInViewer(arg) },
            )

            "page" -> {
                val (docId, index) = arg.split("/")
                PageScreen(vm, docId, index.toInt(), snackbar, onBack = ::pop)
            }
            "text" -> TextScreen(vm, arg, snackbar, onBack = ::pop)
            "settings" -> SettingsScreen(vm, onBack = ::pop)

            ROUTE_TOOLS -> ToolsHomeScreen(
                tvm = tvm,
                snackbar = snackbar,
                bottomBar = bottomBar,
                onTool = { openTool(it, fromHome = true) },
                onOpenFile = { push("viewer/${Uri.encode(it.absolutePath)}") },
                onSaveFile = ::saveFile,
            )

            "tool" -> {
                val kind = ToolKind.valueOf(arg)
                when (kind) {
                    ToolKind.MERGE -> MergeScreen(tvm, scans, snackbar, onBack = ::pop, onPickFiles = {
                        pickMergePdfs.launch(arrayOf("application/pdf"))
                    })
                    ToolKind.ORGANIZE -> OrganizeScreen(tvm, scans, snackbar, onBack = ::pop, onPickFile = {
                        pickToolPdf.launch(arrayOf("application/pdf"))
                    })
                    ToolKind.SIGN -> SignScreen(tvm, scans, snackbar, onBack = ::pop, onPickFile = {
                        pickToolPdf.launch(arrayOf("application/pdf"))
                    })
                    else -> SimpleToolScreen(tvm, kind, scans, snackbar, onBack = ::pop, onPickFile = {
                        pickToolPdf.launch(arrayOf("application/pdf"))
                    })
                }
            }

            "result" -> ResultScreen(
                tvm = tvm,
                snackbar = snackbar,
                onClose = ::pop,
                onOpenFile = { push("viewer/${Uri.encode(it.absolutePath)}") },
                onSaveFile = ::saveFile,
                onContinue = { push("viewer/${Uri.encode(it.absolutePath)}") },
            )

            "viewer" -> {
                val file = File(Uri.decode(arg))
                ViewerScreen(
                    tvm = tvm,
                    file = file,
                    snackbar = snackbar,
                    onBack = ::pop,
                    onSaveFile = ::saveFile,
                    onTool = { kind ->
                        tvm.useFile(file, if (kind == ToolKind.MERGE) InputTarget.MERGE else InputTarget.SINGLE)
                        openTool(kind, fromHome = false)
                    },
                )
            }

            else -> LibraryScreen(
                vm = vm,
                snackbar = snackbar,
                bottomBar = bottomBar,
                onOpen = { push("doc/$it") },
                onScan = { startScan() },
                onImportImages = {
                    pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onImportPdf = { pickPdf.launch(arrayOf("application/pdf")) },
                onSettings = { push("settings") },
            )
        }

        passwordRequest?.let { req ->
            key(req) { TextInputDialog(
                title = if (req.wrong) "Contraseña incorrecta" else "PDF protegido",
                initial = "",
                label = "Contraseña de \"${req.name}\"",
                password = true,
                confirmLabel = "Abrir",
                onDismiss = tvm::cancelPassword,
                onConfirm = tvm::submitPassword,
            ) }
        }

        (busy ?: toolBusy)?.let { BusyOverlay(it) }
    }

    companion object {
        const val ACTION_SCAN = "io.github.littledumbduck.pdfscan.ACTION_SCAN"
        private const val EXTRA_HANDLED = "pdfscan.handled"
        private const val ROUTE_LIBRARY = "library"
        private const val ROUTE_TOOLS = "tools"
    }
}

private fun <T> List<T>.toMutableStateList(): SnapshotStateList<T> = mutableStateListOf<T>().also { it.addAll(this) }
