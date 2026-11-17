# PDFScan (Android)

Escáner de documentos y navaja suiza de PDF para uso personal: rápido, **sin anuncios, sin marcas de agua,
sin cuentas y sin límites**, con todo el procesado en el propio móvil (nada se sube a ningún servidor).

La investigación de apps de referencia (CamScanner, Adobe Scan, Genius Scan, vFlat, Microsoft Lens,
iLovePDF, Smallpdf, Xodo, PDFgear…) y la hoja de ruta están en
[docs/INVESTIGACION_COMPETENCIA.md](docs/INVESTIGACION_COMPETENCIA.md).

## Instalar

1. Descarga el APK de la última versión en
   [Releases](https://github.com/littledumbduck/PDFScan-Android/releases/latest).
2. Ábrelo en el móvil y permite instalar apps desde esa fuente cuando Android lo pida.

Requisitos: **Android 8.0 o superior** y Google Play Services (lo usan el escáner y el OCR).

Con el móvil conectado por USB también se puede instalar con:

```bash
adb install -r PDFScan-v0.2.0.apk
```

## Funciones

La app tiene dos pestañas: **Escaneos** y **Herramientas PDF**.

### Escaneos

**Escanear**
- Escáner de Google (ML Kit Document Scanner): detección automática de bordes, captura automática,
  recorte, filtros y limpieza de sombras, manchas y dedos. Multipágina.
- Importar imágenes de la galería o PDFs existentes (para editarlos, unirlos o pasarles OCR).
- Recibir imágenes/PDFs desde "Compartir" de cualquier app.
- Botón en **Ajustes rápidos** y **acceso directo** (mantener pulsado el icono) para escanear en un toque.

**Editar**
- Añadir páginas a un documento existente, reordenar, rotar, eliminar.
- Filtros por página: Mejorar, Escala de grises, Blanco y negro, Ahorro de tinta (con "restaurar original").
- Seleccionar varias páginas (pulsación larga) → rotar, eliminar o **extraer a un documento nuevo**.
- Seleccionar varios documentos → **unir** en uno (en el orden de selección).

**Texto (OCR)**
- OCR automático en el dispositivo (ML Kit, alfabeto latino) para cualquier documento sin texto:
  escaneos, importaciones y lo añadido desde las herramientas PDF.
- **PDF buscable**: el texto se incrusta como capa invisible (se puede buscar y copiar en cualquier visor).
- Ver, copiar y compartir el texto. Búsqueda en la biblioteca por nombre **y por contenido** (ignora tildes).
- Sugerir nombre del documento a partir del texto.

**Compartir / exportar**
- Compartir como PDF, PDF con contraseña (AES-256), imágenes JPG o texto.
- Guardar el PDF donde quieras (selector del sistema).
- "Firmar / herramientas PDF": abre el escaneo en el visor para usar cualquier herramienta.
- Ajustes: tamaño de página (A4, Carta, ajustado a la imagen), márgenes, calidad/peso del PDF y OCR.

### Herramientas PDF

Funcionan con PDFs del móvil y con tus escaneos; el contenido original (texto, vectores) se conserva.

| Herramienta | Qué hace |
|---|---|
| Unir PDFs | Combina PDFs del móvil y escaneos en el orden que quieras |
| Dividir | Cada página en un PDF, un PDF por rango (`1-3, 5, 8-10`) o extraer rangos en uno |
| Organizar páginas | Reordenar, rotar, eliminar o extraer páginas con miniaturas |
| Firmar | Dibuja tu firma (se guarda para reutilizarla), añade texto o la fecha y colócalos arrastrando |
| Comprimir | 3 niveles; muestra el ahorro y nunca deja el archivo más grande |
| Proteger / Quitar contraseña | Cifrado AES-256; quitarla requiere conocerla |
| Numerar páginas | Posición y formato (`1`, `1 / 10`, `Página 1`) |
| Marca de agua | Texto en diagonal con opacidad ajustable (solo si la pones tú) |
| PDF a imágenes | Cada página como JPG (150/200/300 ppp), compartir o pasar a Escaneos |
| Extraer texto | Copia o comparte el texto de PDFs digitales |
| Imágenes a PDF | Crea un documento desde la galería |

Además:
- **Visor de PDF** y **"Abrir con PDFScan"** desde cualquier gestor de archivos, correo, etc.
- PDFs protegidos: la app pide la contraseña al abrirlos.
- **Mis archivos PDF**: historial de lo creado, con compartir, guardar, renombrar y eliminar.

## Compilar

Requisitos: JDK 17+ y Android SDK con la plataforma 37 (AGP 9.4, Gradle 9.6 vía wrapper).

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Para instalarlo en el móvil conectado por USB:

```bash
./gradlew installDebug
```

> El build `release` se firma con la clave debug (es una app personal), así que
> `./gradlew assembleRelease` genera un APK instalable directamente. Para actualizar sin desinstalar,
> compila siempre con la misma clave (la de `~/.android/debug.keystore` de este PC).

## Stack

Kotlin · Jetpack Compose (Material 3, colores dinámicos) · ML Kit Document Scanner · ML Kit Text Recognition ·
PdfBox-Android (PDF, capa OCR, cifrado, unir/dividir/firmar/comprimir) · PdfRenderer (visor e importar PDF).

```
app/src/main/java/io/github/littledumbduck/pdfscan/
├─ data/      Documentos escaneados (carpetas con JPG + meta.json) y ajustes
├─ ocr/       OCR con ML Kit
├─ pdf/       Generación del PDF de los escaneos (capa de texto, cifrado)
├─ tools/     Herramientas PDF (PdfTools), visor (PdfPreview), firmas
├─ ui/        ViewModel, tema y pantallas (screens/ y screens/tools/)
└─ service/   Botón de Ajustes rápidos
```

Los escaneos se guardan en el almacenamiento interno de la app como carpetas con imágenes JPG y un
`meta.json`; el PDF se genera al compartir/exportar con los ajustes actuales. Los PDFs creados con las
herramientas se guardan en "Mis archivos PDF".

## Versiones

- **v0.2.0** — Herramientas PDF (unir, dividir, organizar, firmar, comprimir, proteger, numerar, marca de
  agua, a imágenes, extraer texto), visor y "Abrir con", y correcciones probadas en un móvil real.
- **v0.1.0** — Escáner, biblioteca, edición de páginas, OCR, PDF buscable y compartir.

## Próximos pasos

Ver la hoja de ruta en [docs/INVESTIGACION_COMPETENCIA.md](docs/INVESTIGACION_COMPETENCIA.md#5-hoja-de-ruta):
cámara propia con modos libro / DNI / pizarra, exportar a Word, carpetas, bloqueo biométrico,
anotaciones (subrayar, resaltar), backup y exportación automática a una carpeta.
