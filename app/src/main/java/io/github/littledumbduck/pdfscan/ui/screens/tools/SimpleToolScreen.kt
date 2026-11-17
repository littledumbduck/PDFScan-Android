package io.github.littledumbduck.pdfscan.ui.screens.tools

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.data.ScanDocument
import io.github.littledumbduck.pdfscan.tools.CompressLevel
import io.github.littledumbduck.pdfscan.tools.InputTarget
import io.github.littledumbduck.pdfscan.tools.NumberFormat
import io.github.littledumbduck.pdfscan.tools.NumberPosition
import io.github.littledumbduck.pdfscan.tools.SplitMode
import io.github.littledumbduck.pdfscan.tools.ToolKind
import io.github.littledumbduck.pdfscan.tools.ToolsViewModel

/** Herramientas de "un PDF + opciones + botón": dividir, comprimir, proteger, numerar, etc. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleToolScreen(
    tvm: ToolsViewModel,
    kind: ToolKind,
    scans: List<ScanDocument>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onPickFile: () -> Unit,
) {
    val input by tvm.input.collectAsStateWithLifecycle()
    var pickScan by rememberSaveable { mutableStateOf(false) }

    // Opciones
    var splitMode by rememberSaveable { mutableStateOf(SplitMode.EACH_PAGE) }
    var ranges by rememberSaveable { mutableStateOf("") }
    var compress by rememberSaveable { mutableStateOf(CompressLevel.MEDIUM) }
    var password by rememberSaveable { mutableStateOf("") }
    var password2 by rememberSaveable { mutableStateOf("") }
    var numberPos by rememberSaveable { mutableStateOf(NumberPosition.BOTTOM_CENTER) }
    var numberFmt by rememberSaveable { mutableStateOf(NumberFormat.N_OF_TOTAL) }
    var wmText by rememberSaveable { mutableStateOf("COPIA") }
    var wmOpacity by rememberSaveable { mutableFloatStateOf(0.25f) }
    var dpi by rememberSaveable { mutableIntStateOf(200) }

    val canRun = input != null && when (kind) {
        ToolKind.SPLIT -> splitMode == SplitMode.EACH_PAGE || ranges.isNotBlank()
        ToolKind.PROTECT -> password.isNotEmpty() && password == password2
        ToolKind.WATERMARK -> wmText.isNotBlank()
        else -> true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text(kind.label) },
            )
        },
        bottomBar = {
            BottomAppBar {
                Button(
                    enabled = canRun,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    onClick = {
                        when (kind) {
                            ToolKind.SPLIT -> tvm.split(splitMode, ranges)
                            ToolKind.COMPRESS -> tvm.compress(compress)
                            ToolKind.PROTECT -> tvm.protect(password)
                            ToolKind.UNLOCK -> tvm.unlock()
                            ToolKind.PAGE_NUMBERS -> tvm.pageNumbers(numberPos, numberFmt)
                            ToolKind.WATERMARK -> tvm.watermark(wmText.trim(), wmOpacity)
                            ToolKind.TO_IMAGES -> tvm.toImages(dpi)
                            ToolKind.EXTRACT_TEXT -> tvm.extractText()
                            else -> Unit
                        }
                    },
                ) { Text(kind.label) }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            InputPicker(input, onPickFile, { pickScan = true }, tvm::clearInput)
            HorizontalDivider()

            when (kind) {
                ToolKind.SPLIT -> {
                    SplitMode.entries.forEach { m -> Radio(m.label, splitMode == m) { splitMode = m } }
                    if (splitMode != SplitMode.EACH_PAGE) {
                        OutlinedTextField(
                            value = ranges,
                            onValueChange = { ranges = it },
                            label = { Text("Páginas (p. ej. 1-3, 5, 8-10)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                        )
                    }
                }

                ToolKind.COMPRESS -> CompressLevel.entries.forEach { l ->
                    Radio(l.label, compress == l, l.description) { compress = l }
                }

                ToolKind.PROTECT -> {
                    PasswordField("Contraseña", password) { password = it }
                    PasswordField("Repite la contraseña", password2) { password2 = it }
                    if (password2.isNotEmpty() && password != password2) {
                        Hint("Las contraseñas no coinciden", error = true)
                    }
                    Hint("Se cifrará con AES-256. Sin la contraseña no se podrá abrir.")
                }

                ToolKind.UNLOCK -> Hint(
                    "Elige el PDF protegido: te pedirá la contraseña al abrirlo. " +
                        "Después se guardará una copia sin contraseña.",
                )

                ToolKind.PAGE_NUMBERS -> {
                    Section("Posición")
                    NumberPosition.entries.forEach { p -> Radio(p.label, numberPos == p) { numberPos = p } }
                    Section("Formato")
                    NumberFormat.entries.forEach { f -> Radio(f.label, numberFmt == f) { numberFmt = f } }
                }

                ToolKind.WATERMARK -> {
                    OutlinedTextField(
                        value = wmText,
                        onValueChange = { wmText = it },
                        label = { Text("Texto") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                    Section("Opacidad: ${(wmOpacity * 100).toInt()} %")
                    Slider(
                        value = wmOpacity,
                        onValueChange = { wmOpacity = it },
                        valueRange = 0.1f..0.6f,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                ToolKind.TO_IMAGES -> {
                    Section("Resolución")
                    listOf(150 to "Normal (150 ppp)", 200 to "Alta (200 ppp)", 300 to "Máxima (300 ppp)").forEach { (v, l) ->
                        Radio(l, dpi == v) { dpi = v }
                    }
                }

                ToolKind.EXTRACT_TEXT -> Hint(
                    "Extrae el texto de PDFs digitales. Si el PDF es una foto o un escaneo sin OCR, " +
                        "impórtalo en Escaneos para reconocer el texto.",
                )

                else -> Unit
            }
        }
    }

    if (pickScan) {
        ScanPickerDialog(
            docs = scans,
            multiple = false,
            onDismiss = { pickScan = false },
            onPick = { tvm.pickScans(it, InputTarget.SINGLE) },
        )
    }
}

@Composable
private fun Radio(label: String, selected: Boolean, description: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = if (description != null) ({ Text(description) }) else null,
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, onClick = onClick),
    )
}

@Composable
private fun Section(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
    )
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
