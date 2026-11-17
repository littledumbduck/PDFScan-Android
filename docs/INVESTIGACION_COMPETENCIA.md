# Investigación de competencia — Apps de escáner PDF en Android

> Fecha: noviembre 2026. Objetivo: identificar lo mejor (y lo peor) de cada app de escaneo de documentos
> en Android para diseñar **PDFScan**, una app que reúna todas las fortalezas sin sus defectos.

---

## 1. Panorama del mercado (2026)

| App | Posicionamiento | Lo mejor | Lo peor | Modelo |
|---|---|---|---|---|
| **CamScanner** | La más completa en funciones | Todo: OCR, firma, marcas de agua, conversión a Word/Excel/PPT, ID cards, "eliminar escritura a mano", traducción, colaboración, carpetas, nube propia | Marca de agua en gratis, paywall agresivo, anuncios, historial de confianza (en 2019 Kaspersky encontró un troyano en una versión de Play Store), suscripción cara | Freemium + suscripción |
| **Adobe Scan** | La referencia en calidad de OCR | Detección de bordes excelente, OCR muy bueno → PDF con texto buscable, reconocimiento de tarjetas de visita → contacto, integración con Acrobat | Obliga a cuenta Adobe y a subir a Adobe Document Cloud; la edición PDF real es de pago (Acrobat) | Gratis + Acrobat Premium |
| **Microsoft Lens** | (Retirada) era la favorita "sin fricción" | Modos Documento / Pizarra / Foto / Tarjeta, exportar a Word/PowerPoint/OneNote, lector inmersivo | **Retirada**: dejó de estar en tiendas en enero 2026 y dejó de escanear el 9 de marzo de 2026; Microsoft redirige a OneDrive. → **Hueco de mercado: muchos usuarios huérfanos** | Gratis |
| **Google Drive (escáner) / ML Kit** | El "por defecto" en Android | Escaneo nativo muy rápido, captura automática, limpieza de manchas y dedos con ML, título sugerido, OCR en Drive | Pocas opciones: casi sin organización local, sin edición PDF, atado a Drive | Gratis |
| **Genius Scan** | Privacidad + profesionales | Procesamiento en el dispositivo, nombres de archivo inteligentes, exportación automática a nubes (Drive, Dropbox, OneDrive, WebDAV, FTP, email), escaneo por lotes rapidísimo, cifrado y bloqueo biométrico | Funciones avanzadas (OCR, sync, exportación) en pago anual | Freemium (Plus/Ultra) |
| **vFlat Scan** | Libros y apuntes | Escaneo de libros a doble página con división automática, **aplanado de la curvatura de la página**, borrado de dedos, disparo por temporizador/voz, OCR gratuito | Enfocado a libros, poca edición PDF | Gratis con límites + premium |
| **TapScanner / Clear Scan / Tiny Scanner / Fast Scanner / SwiftScan** | Clones "ligeros" | Rápidos, UI simple, filtros decentes, QR | Anuncios intrusivos, paywalls, OCR mediocre, marca de agua | Freemium agresivo |
| **Samsung (Cámara / Notes)** | Integrado en la cámara | Detección de documentos desde la propia cámara, sin abrir otra app | Solo Samsung, funciones mínimas | Gratis |
| **PDFgear Scan** | Alternativa "Lens" | Sin anuncios, sin suscripción, sin nube forzada | Menos maduro | Gratis |
| **Xodo / iLovePDF / Smallpdf / PDFelement** | Herramientas PDF (no solo escáner) | Unir, dividir, comprimir, rotar, firmar, rellenar formularios, anotar, convertir PDF↔Office, proteger con contraseña | Muchas conversiones se hacen en servidor (privacidad), límites diarios en gratis | Freemium |
| **FairScan** (open source, GPLv3) | Privacidad pura | Todo local, permisos mínimos, PDF limpio sin ajustes manuales | Muy poco control manual, sin OCR | Gratis |
| **MakeACopy** (Apache-2.0) | Open source con OCR | OCR offline con Tesseract, corrección de perspectiva manual/automática | UI básica | Gratis |
| **OSS Document Scanner / OpenScan** | Open source | Sin anuncios ni recogida de datos, PDF/JPEG | Funciones limitadas | Gratis |

**Conclusión del panorama**

1. **Ninguna app combina** la potencia de CamScanner con la privacidad de Genius Scan/FairScan y la ausencia de abusos (anuncios, marca de agua).
2. La **retirada de Microsoft Lens** (marzo 2026) deja a muchos usuarios buscando algo simple, gratis y con exportación a Office.
3. Las principales quejas de los usuarios en reseñas son siempre las mismas: **marca de agua, paywalls, anuncios, cuentas obligatorias y subir documentos a la nube**.

---

## 2. Matriz de funcionalidades

Leyenda: ✅ completo · 🟡 parcial / de pago · ❌ no tiene

### 2.1 Captura

| Función | CamScanner | Adobe Scan | Lens† | G. Drive | Genius | vFlat | **PDFScan (objetivo)** |
|---|---|---|---|---|---|---|---|
| Detección automática de bordes en tiempo real | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Captura automática al estabilizarse | ✅ | ✅ | 🟡 | ✅ | ✅ | ✅ | ✅ |
| Modo lote (multipágina continuo) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Importar desde galería | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Modo libro (doble página + dividir) | ✅ | ❌ | ❌ | ❌ | 🟡 | ✅ | ✅ |
| Aplanar curvatura de página | 🟡 | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ (v2) |
| Modo DNI / tarjeta (anverso+reverso en 1 página) | ✅ | 🟡 | ❌ | 🟡 | ❌ | ❌ | ✅ |
| Modo pizarra / whiteboard | ✅ | ✅ | ✅ | ❌ | 🟡 | ❌ | ✅ |
| Tarjeta de visita → contacto | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ |
| Lector QR / código de barras | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| Disparo por voz / temporizador / botón de volumen | 🟡 | ❌ | ❌ | ❌ | ❌ | ✅ | ✅ |
| Linterna, guía de nivel, cuadrícula | ✅ | ✅ | ✅ | 🟡 | ✅ | ✅ | ✅ |
| Escaneo desde widget / Quick Settings tile / atajo | 🟡 | 🟡 | ❌ | ✅ | 🟡 | ❌ | ✅ |

† Microsoft Lens retirada en 2026; se incluye como referencia de diseño.

### 2.2 Procesado de imagen

| Función | Referencia | PDFScan |
|---|---|---|
| Recorte manual con lupa en esquinas | Todas | ✅ |
| Corrección de perspectiva | Todas | ✅ |
| Filtros: original, mejorado/auto, B/N, escala de grises, "magic color", ahorro de tinta | CamScanner, Genius | ✅ |
| Eliminar sombras | ML Kit, CamScanner | ✅ |
| Eliminar dedos / manchas | ML Kit (modo full), vFlat | ✅ |
| Eliminar escritura a mano (recuperar formulario en blanco) | CamScanner (pago) | ✅ (v2) |
| Brillo / contraste / nitidez manual | Todas | ✅ |
| Rotar, reordenar, duplicar, borrar páginas | Todas | ✅ |
| Aplicar filtro a todas las páginas | Genius, CamScanner | ✅ |
| Detección automática de orientación del texto | Adobe | ✅ |
| Detección de páginas en blanco (al escanear lotes) | Genius (escáneres de mesa) | ✅ |

### 2.3 OCR y texto

| Función | Referencia | PDFScan |
|---|---|---|
| PDF buscable (capa de texto invisible) | Adobe, Genius (pago), CamScanner (pago) | ✅ **gratis** |
| OCR offline | Genius, MakeACopy, vFlat | ✅ |
| Multiidioma (incl. español, chino, japonés, árabe…) | Adobe, CamScanner | ✅ |
| Copiar / editar / compartir texto extraído | Todas | ✅ |
| Búsqueda de texto en todos los documentos | Adobe, Genius, Drive | ✅ |
| Leer en voz alta (TTS) | Lens (Immersive Reader) | ✅ |
| Traducir texto | CamScanner | ✅ (on-device, ML Kit Translation) |
| Extraer tablas → Excel/CSV | CamScanner, Lens | ✅ (v2) |

### 2.4 Edición de PDF

| Función | Referencia | PDFScan |
|---|---|---|
| Firma (dibujada, guardada, reutilizable) | CamScanner, Adobe Fill&Sign, Xodo | ✅ |
| Rellenar formularios / añadir texto | Xodo, Adobe | ✅ |
| Anotar: subrayar, resaltar, dibujar, notas | Xodo | ✅ (v2) |
| Marca de agua **propia** (opcional, configurable) | CamScanner | ✅ |
| Censurar / ocultar (redacción real, no un rectángulo encima) | PDFelement | ✅ |
| Unir PDFs | iLovePDF, CamScanner | ✅ |
| Dividir / extraer páginas | iLovePDF | ✅ |
| Comprimir (calidad elegible, tamaño objetivo) | iLovePDF, Genius | ✅ |
| Contraseña / cifrado AES-256 | CamScanner, Genius | ✅ |
| Numeración de páginas, encabezado/pie | iLovePDF | ✅ |
| Editar metadatos (título, autor) | PDFelement | ✅ |

### 2.5 Conversiones

| De → A | Referencia | PDFScan | Notas técnicas |
|---|---|---|---|
| Imagen(es) → PDF | Todas | ✅ | Núcleo |
| PDF → JPG/PNG (por página o ZIP) | CamScanner, iLovePDF | ✅ | `PdfRenderer` / PDFium |
| Escaneo → TXT | Todas | ✅ | OCR |
| Escaneo/PDF → Word (.docx) | CamScanner, Lens, Adobe (pago) | ✅ | Generar OOXML desde bloques OCR (en dispositivo) |
| Escaneo → Excel (.xlsx) / CSV | CamScanner, Lens | 🟡 v2 | Requiere detección de tablas |
| Escaneo → PowerPoint | Lens | 🟡 v2 | 1 imagen por diapositiva es trivial |
| Word/Excel/PPT → PDF | iLovePDF, CamScanner | 🟡 v3 | Difícil en dispositivo; opcional vía servidor con consentimiento explícito |
| PDF → PDF/A (archivo) | Genius, Adobe | ✅ v2 | |
| HEIC/WebP/TIFF → PDF | iLovePDF | ✅ | |
| Capturas/fotos largas → PDF | — | ✅ | |
| Escaneo → eBook (EPUB) | — | ❌ | Fuera de alcance |

### 2.6 Organización y almacenamiento

| Función | Referencia | PDFScan |
|---|---|---|
| Carpetas y subcarpetas | Todas | ✅ |
| Etiquetas / favoritos | Genius | ✅ |
| Nombres inteligentes por plantilla (`{fecha}_{tipo}_{texto OCR}`) | Genius | ✅ |
| Sugerencia automática de título (por OCR / tipo de documento) | Google Drive | ✅ |
| Búsqueda por nombre + contenido OCR | Adobe, Genius | ✅ |
| Ordenar / vista lista / cuadrícula | Todas | ✅ |
| Papelera con recuperación (30 días) | CamScanner | ✅ |
| Guardar en almacenamiento local visible (Documentos/PDFScan) | Genius, FairScan | ✅ |
| Copia de seguridad / exportar todo | Genius | ✅ |

### 2.7 Compartir y exportar

| Función | Referencia | PDFScan |
|---|---|---|
| Compartir como PDF / JPG / TXT / ZIP | Todas | ✅ |
| Elegir tamaño al compartir (original / medio / pequeño) | CamScanner, Genius | ✅ |
| Enviar por email con asunto/cuerpo predefinidos | Genius | ✅ |
| Exportación automática a nube (Drive, Dropbox, OneDrive, WebDAV, Nextcloud, SFTP) | Genius (pago) | ✅ v2 (vía SAF / WebDAV) |
| Imprimir | Todas | ✅ (framework de impresión Android) |
| Fax | CamScanner, Genius (créditos) | ❌ |
| Enlace compartido / colaboración | CamScanner, Adobe | ❌ (requiere backend; no es objetivo) |
| Recibir "Compartir con" desde otras apps (imágenes/PDF) | Todas | ✅ |

### 2.8 Seguridad y privacidad

| Función | Referencia | PDFScan |
|---|---|---|
| 100 % offline, sin cuenta | FairScan, Genius | ✅ **principio de diseño** |
| Bloqueo con huella / PIN | Genius, CamScanner | ✅ |
| Documentos cifrados en reposo | Genius | ✅ |
| Sin permiso de cámara propio (usar ML Kit) en modo rápido | Apps con ML Kit | ✅ |
| Sin anuncios ni trackers | FairScan, OpenScan | ✅ |
| Sin marca de agua | — | ✅ **siempre** |

### 2.9 "Calidad de vida"

- Widget de pantalla de inicio + **tile en Ajustes rápidos** para escanear en 1 toque.
- Material You (colores dinámicos), modo oscuro, tablet/plegables.
- Accesibilidad: TalkBack, guía por voz al encuadrar ("mueve a la izquierda", "acércate") — ninguna app grande lo hace bien.
- Deshacer en todas las acciones de edición.
- Onboarding de 0 pantallas: abrir → cámara.

---

## 3. Anti-patrones a evitar (quejas recurrentes de usuarios)

1. **Marca de agua** en documentos gratuitos (CamScanner, clones).
2. **Anuncios a pantalla completa** tras cada escaneo (TapScanner, Clear Scan, Fast Scanner).
3. **Paywall al abrir la app** o "prueba gratis" que se convierte en suscripción anual.
4. **Cuenta obligatoria** y subida forzada a la nube (Adobe Scan).
5. **OCR secuestrado**: limitar el OCR a X páginas/mes.
6. **Exportación limitada**: no dejar guardar el PDF donde quiere el usuario.
7. **Pérdida de documentos** al cambiar de móvil (sin backup claro).
8. **Apps que desaparecen** (Microsoft Lens) → los formatos deben ser estándar (PDF/JPG en carpetas normales), nunca una base de datos opaca.

---

## 4. Propuesta de valor de PDFScan

> **"La potencia de CamScanner, la calidad de Adobe Scan y la privacidad de Genius Scan — sin anuncios, sin marca de agua y sin cuenta."**

Principios:

1. **Local-first**: todo el procesado (detección, filtros, OCR, conversión) ocurre en el dispositivo.
2. **Formatos abiertos**: los documentos viven como PDF reales en una carpeta accesible.
3. **Rápido**: de abrir la app a PDF compartido en < 10 s.
4. **Gratis lo esencial**: escanear, OCR, PDF buscable, unir/dividir/comprimir, firmar, compartir.
5. **Progresivo**: modo simple por defecto, herramientas avanzadas a un toque.

---

## 5. Hoja de ruta

### MVP (v0.1)
- Escaneo con **ML Kit Document Scanner** (detección, recorte, filtros, limpieza de sombras/dedos, galería).
- Generación de PDF multipágina (tamaño A4/Carta/automático, márgenes, calidad).
- Biblioteca local: lista/cuadrícula, renombrar, borrar, carpetas, búsqueda por nombre.
- Visor de PDF.
- Compartir (PDF/JPG) y "Abrir con" / recibir imágenes desde otras apps.
- Material 3 + modo oscuro.

### v0.5
- OCR (ML Kit Text Recognition v2) → **PDF buscable** con capa de texto invisible.
- Búsqueda de contenido en todos los documentos.
- Herramientas PDF: unir, dividir, reordenar, rotar, comprimir, contraseña.
- Firma reutilizable, añadir texto/fecha.
- Nombres inteligentes y sugerencia de título.
- Widget + tile de Ajustes rápidos.
- Bloqueo biométrico.

### v1.0
- **Cámara propia** (CameraX + OpenCV) con modos: Documento, Libro (doble página), DNI/Tarjeta, Pizarra, QR, Tarjeta de visita.
- Exportar a Word (.docx) y TXT; PDF → imágenes.
- Traducción on-device, lectura en voz alta.
- Papelera, backup/restauración (ZIP), exportación automática a carpeta SAF / WebDAV.
- Detección de páginas en blanco.

### v2.0
- Aplanado de curvatura de libros, eliminar escritura a mano.
- Tablas → Excel/CSV.
- Anotaciones completas y redacción (censura real).
- PDF/A.
- Guía de encuadre por voz (accesibilidad).

---

## 6. Stack técnico recomendado

| Área | Elección | Por qué |
|---|---|---|
| Lenguaje / UI | **Kotlin + Jetpack Compose (Material 3)** | Estándar moderno de Android |
| Arquitectura | MVVM + Clean (módulos `core`, `data`, `feature-*`), **Hilt**, Coroutines/Flow | Escalable y testeable |
| Escaneo rápido | **ML Kit Document Scanner** (`play-services-mlkit-document-scanner`) | UI de escaneo de Google, en dispositivo, sin permiso de cámara, ML para limpiar manchas/dedos, muy poco peso en el APK |
| Cámara propia (v1) | **CameraX** + **OpenCV** (detección de contornos, warp, filtros) | Control total para modos libro/DNI/pizarra |
| OCR | **ML Kit Text Recognition v2** (latino, chino, devanagari, japonés, coreano); Tesseract (tess-two/tesseract4android) como alternativa para más idiomas | Offline y gratis |
| Traducción | ML Kit Translation (on-device) | Offline |
| Códigos | ML Kit Barcode Scanning | QR / códigos de barras |
| Generar/editar PDF | **PdfBox-Android** (Apache-2.0) | Capa de texto OCR, unir, dividir, cifrar, metadatos. `android.graphics.pdf.PdfDocument` vale para PDF de imágenes simples |
| Renderizar PDF | `android.graphics.pdf.PdfRenderer` / **androidx.pdf** (visor Jetpack) | Nativo, sin licencias problemáticas |
| DOCX | Escritor OOXML propio ligero (zip + XML) | Apache POI es muy pesado en Android |
| Persistencia | **Room** (metadatos, texto OCR con FTS4 para búsqueda), **DataStore** (ajustes) | Búsqueda de texto completo rápida |
| Ficheros | Almacenamiento interno de la app + exportación por **Storage Access Framework**; **FileProvider** para compartir | Compatible con Scoped Storage |
| Trabajo en segundo plano | **WorkManager** (OCR por lotes, exportación automática) | Robusto |
| Imágenes | Coil (miniaturas) | Ligero y Compose-friendly |
| Seguridad | BiometricPrompt, Jetpack Security / Tink para cifrado | Bloqueo y cifrado en reposo |
| minSdk / targetSdk | minSdk 24, targetSdk 35+ | Cubre ~97 % de dispositivos |

⚠️ **Licencias a evitar** en una app cerrada: **iText** y **MuPDF** son AGPL (obligarían a liberar el código o pagar licencia comercial).

### Estructura de módulos propuesta

```
app/
core/
  ├─ ui/            (tema, componentes Compose)
  ├─ model/
  ├─ database/      (Room + FTS)
  ├─ pdf/           (PdfBox: crear, unir, dividir, cifrar, capa OCR)
  ├─ imaging/       (OpenCV: filtros, perspectiva)
  └─ ocr/           (ML Kit / Tesseract)
feature/
  ├─ scanner/       (ML Kit flow + cámara propia)
  ├─ editor/        (páginas: recorte, filtros, orden)
  ├─ library/       (carpetas, búsqueda)
  ├─ viewer/        (visor PDF)
  ├─ tools/         (unir, dividir, comprimir, convertir, firmar)
  └─ settings/
```

---

## 7. Monetización justa (opcional)

Si algún día se monetiza, evitar los anti-patrones de la sección 3:

- **Todo lo esencial gratis para siempre** (escanear, OCR, PDF buscable, compartir, sin marca de agua).
- Pago único "Pro" (no suscripción) para extras avanzados: exportación automática a nubes, tablas→Excel, temas, etc.
- Sin anuncios. Sin cuentas.

---

## Fuentes

- [Best Scanner Apps in 2026 — TechnBrains](https://www.technbrains.com/blog/best-scanner-apps/)
- [Best Document Scanner Apps for Android in 2026 — DroidLore](https://droidlore.com/productivity/productivity-apps-documents)
- [9 Best Scanner Apps for Android 2026 — bestappsforandroid.com](https://bestappsforandroid.com/best-scanner-apps-for-android/)
- [Best Mobile OCR Apps 2026 — imagetotable.ai](https://imagetotable.ai/blog/best-mobile-ocr-apps-2026)
- [Best Document Scanner Apps in 2026 — Buildwize](https://www.buildwize.app/blog/best-document-scanner-apps-2026/)
- [Retirement of Microsoft Lens — Microsoft Support](https://support.microsoft.com/en-us/lens/)
- [Microsoft Lens really is closing down in a matter of weeks — BetaNews](https://betanews.com/2026/01/13/microsoft-lens-really-is-closing-down-in-a-matter-of-weeks/)
- [Microsoft Lens has been retired — Neowin](https://www.neowin.net/news/microsoft-lens-has-been-retired/)
- [Best Microsoft Lens Alternatives — PDFgear](https://www.pdfgear.com/scan-pdf/microsoft-lens-alternatives.htm)
- [Document scanner with ML Kit on Android — Google Developers](https://developers.google.com/ml-kit/vision/doc-scanner/android)
- [ML Kit Document Scanner API — Android Developers Blog](https://android-developers.googleblog.com/2024/02/ml-kit-document-scanner-api.html)
- [FairScan — Privacy Guides discussion](https://discuss.privacyguides.net/t/fairscan-a-simple-and-respectful-document-scanner-for-android/31915)
- [MakeACopy — IzzyOnDroid F-Droid repo](https://apt.izzysoft.de/ftp/repo/fdroid/index/apk/de.schliweb.makeacopy?repo=main)
