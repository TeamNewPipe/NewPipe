<p align="center"><a href="https://github.com/wizdom13/NewPipe"><img src="../assets/newpip_material_logo.png" width="150" alt="Icono de NewPipe Material"></a></p>

<h1 align="center">NewPipe Material</h1>

<p align="center"><b>Una bifurcación independiente de NewPipe para Android centrada en Material 3.</b></p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL%20v3-blue.svg" alt="Licencia: GPLv3"></a>
  <a href="https://github.com/wizdom13/NewPipe/actions"><img src="https://github.com/wizdom13/NewPipe/actions/workflows/ci.yml/badge.svg" alt="Estado de compilación"></a>
</p>

<p align="center"><b>Leer en:</b> <a href="../README.md">English</a> &bull; <a href="README.de.md">Deutsch</a> &bull; <a href="README.es.md">Español</a> &bull; <a href="README.fr.md">Français</a> &bull; <a href="README.hi.md">हिन्दी</a> &bull; <a href="README.it.md">Italiano</a> &bull; <a href="README.ko.md">한국어</a> &bull; <a href="README.pt_BR.md">Português Brasil</a> &bull; <a href="README.pl.md">Polski</a> &bull; <a href="README.pa.md">ਪੰਜਾਬੀ</a> &bull; <a href="README.ja.md">日本語</a> &bull; <a href="README.ro.md">Română</a> &bull; <a href="README.so.md">Soomaali</a> &bull; <a href="README.tr.md">Türkçe</a> &bull; <a href="README.zh_TW.md">正體中文</a> &bull; <a href="README.asm.md">অসমীয়া</a> &bull; <a href="README.sr.md">Српски</a> &bull; <a href="README.ar.md">العربية</a></p>

---

## Aviso importante sobre la bifurcación

NewPipe Material es una bifurcación mantenida de forma independiente de NewPipe, centrada en el diseño Material 3, los temas de la aplicación y el pulido del producto.

Este proyecto **no está afiliado, patrocinado ni respaldado** por el proyecto oficial NewPipe, TeamNewPipe ni NewPipe e.V.

NewPipe Material se basa en NewPipe y conserva la licencia libre de NewPipe, los créditos del proyecto original y los avisos de licencias de terceros.

---

## ¿Qué es NewPipe Material?

NewPipe Material conserva la experiencia principal de NewPipe mientras moderniza la identidad de la aplicación y su interfaz.

Objetivos actuales de esta bifurcación:

- Superficies, diálogos, ajustes, pestañas y navegación inspirados en Material 3
- Soporte de colores dinámicos Material You cuando esté disponible
- Colores de tema manuales: Predeterminado de la app, Neutral, Verde, Azul, Morado, Naranja, Rosa y Rojo
- Nueva identidad de aplicación: **NewPipe Material**
- ID de aplicación independiente: `org.wisso.newpipematerial`
- Las compilaciones de depuración se instalan por separado como `org.wisso.newpipematerial.debug`
- Conserva el comportamiento de NewPipe, la compatibilidad de importación/exportación y los servicios compatibles

Esta bifurcación evita cambios de comportamiento arriesgados en áreas sensibles como reproducción, descargas, reproducción en segundo plano, reproductor emergente y lógica del Extractor, salvo que se traten como cambios específicos y probados.

---

## Capturas de pantalla

### Teléfono

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/01.png" width="160" alt="Captura de teléfono 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/02.png" width="160" alt="Captura de teléfono 2"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/03.png" width="160" alt="Captura de teléfono 3"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/04.png" width="160" alt="Captura de teléfono 4"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/05.png" width="160" alt="Captura de teléfono 5"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/06.png" width="160" alt="Captura de teléfono 6"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/07.png" width="160" alt="Captura de teléfono 7"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/08.png" width="160" alt="Captura de teléfono 8"></a>
  <a href="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/phoneScreenshots/09.png" width="160" alt="Captura de teléfono 9"></a>
</p>

### Tableta

<p align="center">
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/09.png" width="405" alt="Captura de tableta 1"></a>
  <a href="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png"><img src="../fastlane/metadata/android/en-US/images/tenInchScreenshots/10.png" width="405" alt="Captura de tableta 2"></a>
</p>

---

## Servicios compatibles

NewPipe Material hereda el soporte de NewPipe para:

- YouTube y YouTube Music
- PeerTube
- Bandcamp
- SoundCloud
- media.ccc.de

El soporte de servicios depende del código original de NewPipe y NewPipe Extractor.

---

## Funciones

NewPipe Material conserva las funciones conocidas de NewPipe:

- Ver vídeos y transmisiones en directo
- Reproducción en segundo plano
- Reproductor emergente
- Listas locales
- Suscripciones sin iniciar sesión en una cuenta de plataforma
- Grupos de canales y feeds
- Búsqueda y navegación por servicios compatibles
- Detalles de vídeo, vídeos relacionados y comentarios cuando estén disponibles
- Descarga de vídeo, audio y subtítulos cuando esté disponible
- Importar/exportar datos para migración y copia de seguridad

Añadidos centrados en Material:

- Roles de color Material 3 en más superficies de la app
- Navegación inferior con cinco pestañas principales o menos, con TabLayout desplazable como alternativa
- Posición inferior predeterminada para nuevas instalaciones o valores no configurados
- Soporte de colores de tema dinámicos y manuales
- Atribución de la bifurcación en la pantalla Acerca de
- Soporte de firmado de versiones para compilaciones del fork

---

## Instalación

Instala NewPipe Material desde las versiones de GitHub de este repositorio o desde artefactos firmados cuando estén disponibles.

NewPipe Material usa un ID de aplicación distinto, por lo que puede instalarse junto a NewPipe oficial:

```text
NewPipe oficial:   org.schabi.newpipe / net.newpipe.app según la compilación original
NewPipe Material:  org.wisso.newpipematerial
Debug:             org.wisso.newpipematerial.debug
```

### Migrar datos

1. Abre NewPipe oficial.
2. Exporta la base de datos desde Settings > Backup and Restore.
3. Instala NewPipe Material.
4. Importa la base de datos exportada desde Settings > Backup and Restore.

Haz siempre una copia de seguridad antes de importar datos entre compilaciones.

### Aviso sobre Google Play

No publiques NewPipe Material, NewPipe ni bifurcaciones de NewPipe en Google Play. Este proyecto sigue la misma advertencia práctica de distribución que NewPipe original.

---

## Compilar desde el código fuente

Requisitos:

- JDK 21
- Android SDK
- Gradle wrapper de este repositorio

Comandos útiles:

```bash
./gradlew runCheckstyle -DskipFormatKtlint
./gradlew assembleDebug lintDebug testDebugUnitTest --stacktrace -DskipFormatKtlint
```

Compilar APK de depuración:

```bash
./gradlew assembleDebug -DskipFormatKtlint
```

El APK de depuración usa el nombre **NewPipe Material Debug** y el paquete `org.wisso.newpipematerial.debug`.

---

## Firmado de versión

El firmado se configura con estas variables de entorno:

```text
NEWPIPE_MATERIAL_RELEASE_STORE_FILE
NEWPIPE_MATERIAL_RELEASE_STORE_PASSWORD
NEWPIPE_MATERIAL_RELEASE_KEY_ALIAS
NEWPIPE_MATERIAL_RELEASE_KEY_PASSWORD
```

Si las cuatro variables están presentes, la compilación de lanzamiento usa la clave configurada. Si faltan, no se aplica la configuración de firmado.

Validación recomendada:

```bash
./gradlew assembleRelease -DskipFormatKtlint
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

---

## Estado de desarrollo

NewPipe Material está en desarrollo activo para pulido Material 3 y preparación del producto.

Completado o en progreso:

- Nombre e ID de aplicación
- Separación de identidad debug/release
- Colores Material 3
- Colores de tema dinámicos/manuales
- Navegación inferior y pulido de pestañas principales
- Atribución del fork en Acerca de
- Pulido de diálogos, snackbars, ajustes, detalles de vídeo e interfaz de descarga
- Flujo de firmado de versiones

Aplazado o de alto riesgo:

- Rediseño visual de la capa principal del reproductor
- Colores de barra de progreso y gestos
- Controles de cola
- Comportamiento de menús de calidad/audio/subtítulos
- Cambios amplios en reproducción o descargas

Estas áreas necesitan QA dedicado antes de cambios visuales o de comportamiento.

---

## Contribuir

Las contribuciones son bienvenidas, especialmente pulido Material 3 enfocado, correcciones, hallazgos de QA, documentación y preparación de lanzamientos.

Mantén los cambios enfocados y comprobables. Para cambios de interfaz, incluye capturas antes/después cuando sea posible y comprueba Light, Dark, Black, Follow system y al menos un color manual.

---

## NewPipe original

NewPipe Material está basado en NewPipe.

Recursos originales:

- Repositorio NewPipe: https://github.com/TeamNewPipe/NewPipe
- Sitio web NewPipe: https://newpipe.net
- FAQ de NewPipe: https://newpipe.net/FAQ/
- NewPipe Extractor: https://github.com/TeamNewPipe/NewPipeExtractor

Los problemas específicos de diseño, identidad, lanzamiento o Material 3 del fork pertenecen a este repositorio. Los fallos de servicios o Extractor pueden necesitar comparación con NewPipe oficial.

---

## Donar

Si quieres apoyar a NewPipe original, visita la página oficial de donaciones:

https://newpipe.net/donate

NewPipe Material es una bifurcación independiente; las donaciones al proyecto original van al proyecto NewPipe original, no automáticamente a este fork.

---

## Licencia

NewPipe Material es software libre basado en NewPipe y se distribuye bajo la GNU General Public License versión 3 o posterior.

Consulta los archivos de licencia del repositorio y la pantalla de licencias dentro de la app para detalles completos.
