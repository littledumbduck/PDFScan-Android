package io.github.littledumbduck.pdfscan.data

import org.json.JSONArray
import org.json.JSONObject

/** Una línea de texto reconocida por OCR, con su caja en píxeles de la imagen analizada. */
data class OcrLine(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/** Resultado de OCR de una página. [width]/[height] son las dimensiones de la imagen analizada. */
data class OcrResult(
    val width: Int,
    val height: Int,
    val lines: List<OcrLine>,
) {
    val text: String get() = lines.joinToString("\n") { it.text }
}

/**
 * Página de un documento.
 * @param file nombre del JPG dentro de la carpeta `pages/` del documento.
 * @param original nombre del JPG original sin filtros (si se ha aplicado alguno).
 */
data class Page(
    val file: String,
    val ocr: OcrResult? = null,
    val original: String? = null,
)

data class ScanDocument(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pages: List<Page>,
) {
    val fullText: String
        get() = pages.mapNotNull { it.ocr?.text?.takeIf(String::isNotBlank) }.joinToString("\n\n")

    val hasOcr: Boolean get() = pages.isNotEmpty() && pages.all { it.ocr != null }
}

// ---------------------------------------------------------------------------------------------
// Serialización JSON (meta.json de cada documento). Se usa org.json para no añadir dependencias.
// ---------------------------------------------------------------------------------------------

fun ScanDocument.toJson(): JSONObject = JSONObject().apply {
    put("version", 1)
    put("id", id)
    put("name", name)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
    put("pages", JSONArray().apply { pages.forEach { put(it.toJson()) } })
}

private fun Page.toJson(): JSONObject = JSONObject().apply {
    put("file", file)
    original?.let { put("original", it) }
    ocr?.let { o ->
        put("ocr", JSONObject().apply {
            put("width", o.width)
            put("height", o.height)
            put("lines", JSONArray().apply {
                o.lines.forEach { l ->
                    put(JSONObject().apply {
                        put("t", l.text)
                        put("l", l.left); put("tp", l.top); put("r", l.right); put("b", l.bottom)
                    })
                }
            })
        })
    }
}

fun scanDocumentFromJson(json: JSONObject): ScanDocument {
    val pagesJson = json.optJSONArray("pages") ?: JSONArray()
    val pages = (0 until pagesJson.length()).map { i ->
        val p = pagesJson.getJSONObject(i)
        val ocr = p.optJSONObject("ocr")?.let { o ->
            val linesJson = o.optJSONArray("lines") ?: JSONArray()
            OcrResult(
                width = o.getInt("width"),
                height = o.getInt("height"),
                lines = (0 until linesJson.length()).map { j ->
                    val l = linesJson.getJSONObject(j)
                    OcrLine(l.getString("t"), l.getInt("l"), l.getInt("tp"), l.getInt("r"), l.getInt("b"))
                },
            )
        }
        Page(
            file = p.getString("file"),
            ocr = ocr,
            original = if (p.has("original")) p.getString("original") else null,
        )
    }
    return ScanDocument(
        id = json.getString("id"),
        name = json.getString("name"),
        createdAt = json.getLong("createdAt"),
        updatedAt = json.getLong("updatedAt"),
        pages = pages,
    )
}
