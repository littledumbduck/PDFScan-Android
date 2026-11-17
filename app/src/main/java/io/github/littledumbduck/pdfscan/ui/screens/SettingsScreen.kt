package io.github.littledumbduck.pdfscan.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.littledumbduck.pdfscan.data.PageSize
import io.github.littledumbduck.pdfscan.data.Quality
import io.github.littledumbduck.pdfscan.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás") } },
                title = { Text("Ajustes") },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            Section("Tamaño de página del PDF")
            PageSize.entries.forEach { size ->
                RadioRow(size.label, s.pageSize == size) { vm.updateSettings { it.copy(pageSize = size) } }
            }
            SwitchRow(
                title = "Márgenes blancos",
                subtitle = "Deja un pequeño margen alrededor de la imagen (A4 / Carta)",
                checked = s.margins,
                enabled = s.pageSize != PageSize.AUTO,
            ) { v -> vm.updateSettings { it.copy(margins = v) } }

            HorizontalDivider()
            Section("Calidad / tamaño del PDF")
            Quality.entries.forEach { q ->
                RadioRow(q.label, s.quality == q) { vm.updateSettings { it.copy(quality = q) } }
            }

            HorizontalDivider()
            Section("Texto (OCR)")
            SwitchRow(
                title = "Reconocer texto automáticamente",
                subtitle = "Tras escanear o importar, en el propio móvil",
                checked = s.autoOcr,
            ) { v -> vm.updateSettings { it.copy(autoOcr = v) } }
            SwitchRow(
                title = "PDF buscable",
                subtitle = "Incluye una capa de texto invisible para poder buscar y copiar texto en el PDF",
                checked = s.searchablePdf,
            ) { v -> vm.updateSettings { it.copy(searchablePdf = v) } }

            HorizontalDivider()
            Section("Acceso rápido")
            Text(
                "Puedes añadir el botón \"Escanear\" a los Ajustes rápidos del sistema (desliza dos veces desde arriba → editar), " +
                    "o mantener pulsado el icono de la app para el acceso directo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.selectable(selected = selected, onClick = onClick),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
    )
}
