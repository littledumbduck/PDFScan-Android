package io.github.littledumbduck.pdfscan.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PageSize(val label: String, val widthPt: Float, val heightPt: Float) {
    AUTO("Ajustar a la imagen", 0f, 0f),
    A4("A4", 595.28f, 841.89f),
    LETTER("Carta (US Letter)", 612f, 792f),
}

/** Calidad de las imágenes dentro del PDF. maxPx ≈ lado largo de un A4 a 300/200/150 ppp. */
enum class Quality(val label: String, val maxPx: Int, val jpeg: Int) {
    HIGH("Alta (300 ppp)", 3508, 90),
    MEDIUM("Media (200 ppp)", 2339, 80),
    LOW("Baja / ligera (150 ppp)", 1754, 60),
}

data class AppSettings(
    val pageSize: PageSize = PageSize.A4,
    val quality: Quality = Quality.MEDIUM,
    /** Ejecutar OCR automáticamente tras escanear. */
    val autoOcr: Boolean = true,
    /** Incluir la capa de texto invisible en el PDF (PDF buscable / seleccionable). */
    val searchablePdf: Boolean = true,
    /** Margen blanco alrededor de la imagen en tamaños A4/Carta. */
    val margins: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            pageSize = enumOr(prefs.getString("pageSize", null), d.pageSize),
            quality = enumOr(prefs.getString("quality", null), d.quality),
            autoOcr = prefs.getBoolean("autoOcr", d.autoOcr),
            searchablePdf = prefs.getBoolean("searchablePdf", d.searchablePdf),
            margins = prefs.getBoolean("margins", d.margins),
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val s = transform(_settings.value)
        prefs.edit()
            .putString("pageSize", s.pageSize.name)
            .putString("quality", s.quality.name)
            .putBoolean("autoOcr", s.autoOcr)
            .putBoolean("searchablePdf", s.searchablePdf)
            .putBoolean("margins", s.margins)
            .apply()
        _settings.value = s
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
