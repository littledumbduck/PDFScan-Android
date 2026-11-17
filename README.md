# PDFScan (Android)

Escáner de documentos a PDF para uso personal: rápido, **sin anuncios, sin marcas de agua, sin cuentas**
y con todo el procesado en el propio móvil.

La investigación de apps de referencia (CamScanner, Adobe Scan, Genius Scan, vFlat, Microsoft Lens…)
y la hoja de ruta están en [docs/INVESTIGACION_COMPETENCIA.md](docs/INVESTIGACION_COMPETENCIA.md).

## Funciones

**Escanear**
- Escáner de Google (ML Kit Document Scanner): detección automática de bordes, captura automática,
  recorte, filtros y limpieza de sombras, manchas y dedos. Multipágina.
- Importar imágenes de la galería o PDFs existentes (para editarlos, unirlos o pasarles OCR).
- Recibir imágenes/PDFs desde "Compartir" de cualquier app.
- Botón en **Ajustes rápidos** y **acceso directo** (mantener pulsado el icono) para escanear en un toque.

**Editar**
- Añadir páginas a un documento existente, reordenar, rotar, eliminar.
- Filtros por página: Mejorar, Escala de grises, Blanco y negro, Ahorro de tinta (con "restaurar original").
- Seleccionar varias páginas → rotar, eliminar o **extraer a un documento nuevo** (dividir).
- Seleccionar varios documentos → **unir** en uno (en el orden de selección).

**Texto (OCR)**
- OCR automático en el dispositivo (ML Kit, alfabeto latino).
- **PDF buscable**: el texto se incrusta como capa invisible (se puede buscar y copiar en cualquier visor).
- Ver, copiar y compartir el texto. Búsqueda en la biblioteca por nombre **y por contenido** (ignora tildes).
- Sugerir nombre del documento a partir del texto.

**Compartir / exportar**
- Compartir como PDF, PDF con contraseña (AES-256), imágenes JPG o texto.
- Guardar el PDF donde quieras (selector del sistema).
- Ajustes: tamaño de página (A4, Carta, ajustado a la imagen), márgenes y calidad/peso del PDF.

**Herramientas PDF (v0.2)** — pestaña "Herramientas PDF", todo en el móvil y sin límites
- **Unir** PDFs del móvil y escaneos en el orden que quieras.
- **Dividir** cada página o por rangos (`1-3, 5, 8-10`).
- **Organizar páginas**: reordenar, rotar, eliminar o extraer, con miniaturas.
- **Firmar**: dibuja tu firma (se guarda para reutilizarla), añade texto o la fecha y colócalos arrastrando.
- **Comprimir** (3 niveles, muestra el ahorro), **proteger** con contraseña y **quitar contraseña**.
- **Numerar páginas**, **marca de agua** de texto (opcional), **PDF a imágenes**, **extraer texto**.
- **Visor de PDF** y "Abrir con PDFScan" desde cualquier gestor de archivos; los escaneos se pueden
  abrir en las herramientas ("Firmar / herramientas PDF").
- "Mis archivos PDF": historial de lo creado, con compartir, guardar, renombrar y eliminar.

## Compilar

Requisitos: JDK 17+, Android SDK con la plataforma 37.

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. Para instalarlo en el móvil conectado por USB:

```bash
./gradlew installDebug
```

> El build `release` se firma con la clave debug (es una app personal), así que
> `./gradlew assembleRelease` genera un APK instalable directamente.

## Stack

Kotlin · Jetpack Compose (Material 3, colores dinámicos) · ML Kit Document Scanner · ML Kit Text Recognition ·
PdfBox-Android (PDF, capa OCR, cifrado, unir/dividir/firmar) · PdfRenderer (visor e importar PDF).

Los documentos se guardan en el almacenamiento interno de la app como carpetas con imágenes JPG y un
`meta.json`; el PDF se genera al compartir/exportar con los ajustes actuales.

## Próximos pasos

Ver la hoja de ruta en [docs/INVESTIGACION_COMPETENCIA.md](docs/INVESTIGACION_COMPETENCIA.md#5-hoja-de-ruta):
cámara propia con modos libro / DNI / pizarra, exportar a Word, carpetas, bloqueo biométrico,
anotaciones (subrayar, resaltar), backup y exportación automática a una carpeta.
