package io.github.littledumbduck.pdfscan.tools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolKind(val label: String, val description: String, val icon: ImageVector) {
    MERGE("Unir PDFs", "Combina varios PDFs o escaneos en uno", Icons.Default.Layers),
    SPLIT("Dividir", "Separa por páginas o rangos", Icons.Default.ContentCut),
    ORGANIZE("Organizar páginas", "Reordena, rota, elimina o extrae", Icons.Default.ViewModule),
    SIGN("Firmar", "Añade tu firma, texto o la fecha", Icons.Default.Draw),
    COMPRESS("Comprimir", "Reduce el tamaño del archivo", Icons.Default.Compress),
    PROTECT("Proteger", "Añade contraseña (AES-256)", Icons.Default.Lock),
    UNLOCK("Quitar contraseña", "Si conoces la contraseña", Icons.Default.LockOpen),
    PAGE_NUMBERS("Numerar páginas", "Añade números de página", Icons.Default.FormatListNumbered),
    WATERMARK("Marca de agua", "Texto en diagonal (opcional)", Icons.Default.WaterDrop),
    TO_IMAGES("PDF a imágenes", "Cada página como JPG", Icons.Default.Image),
    EXTRACT_TEXT("Extraer texto", "Copia el texto de un PDF", Icons.Default.TextFields),
    IMAGES_TO_PDF("Imágenes a PDF", "Crea un PDF desde la galería", Icons.Default.PhotoLibrary),
}
