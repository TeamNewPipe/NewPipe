# PipePlay — Folder Structure

```
PipePlay/
├── .editorconfig
├── .gitignore
├── build.gradle.kts
├── FEATURE_PORT_REPORT.md
├── gradle.properties
├── gradlew / gradlew.bat
├── LICENSE
├── README.md
├── settings.gradle / settings.gradle.kts
├── translator.py
│
├── .github/
│   ├── CONTRIBUTING.md
│   ├── FUNDING.yml
│   ├── PULL_REQUEST_TEMPLATE.md
│   ├── changed-lines-count-labeler.yml
│   ├── DISCUSSION_TEMPLATE/
│   │   └── questions.yml
│   ├── ISSUE_TEMPLATE/
│   │   ├── bug_report.yml
│   │   ├── config.yml
│   │   └── feature_request.yml
│   └── workflows/
│       ├── backport-pr.yml
│       ├── build-release-apk.yml
│       ├── ci.yml
│       ├── image-minimizer.js / image-minimizer.yml
│       ├── no-response.yml
│       ├── pr-labeler.yml
│       └── release-apk.yml
│
├── app/                                  # Android application module
│   ├── build.gradle / build.gradle.kts
│   ├── lint.xml
│   ├── proguard-rules.pro
│   ├── sampledata/
│   │   └── channels.json
│   ├── schemas/                          # Room DB migration schemas
│   │   └── org.schabi.newpipe.database.AppDatabase/
│   │       └── 2.json … 901.json
│   └── src/
│       ├── androidTest/                  # Instrumented tests
│       │   └── java/org/schabi/newpipe/
│       │       ├── database/             # DatabaseMigrationTest, FeedDAOTest
│       │       ├── error/                # ErrorInfoTest
│       │       ├── extractor/services/youtube/
│       │       ├── local/
│       │       │   ├── history/          # HistoryRecordManagerTest
│       │       │   ├── playlist/         # LocalPlaylistManagerTest
│       │       │   └── subscription/     # SubscriptionManagerTest
│       │       ├── player/               # SabrPlaybackSmokeTest, benchmark tests
│       │       ├── testUtil/             # TestDatabase, TrampolineSchedulerRule
│       │       └── util/                 # StreamItemAdapterTest
│       │
│       ├── debug/                        # Debug-only sources
│       │   ├── AndroidManifest.xml
│       │   └── java/org/schabi/newpipe/
│       │       ├── DebugApp.kt
│       │       └── settings/DebugSettingsBVDLeakCanary.java
│       │
│       ├── main/                         # Main application sources
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── ejs/                  # YouTube solver JS bundles
│       │   │   ├── apache2.html / epl1.html / gpl_3.html / mit.html / mpl2.html
│       │   │   ├── po_token.html
│       │   │   └── sabr_po_token.js
│       │   │
│       │   ├── java/
│       │   │   ├── androidx/fragment/app/
│       │   │   │   └── FragmentStatePagerAdapterMenuWorkaround.java
│       │   │   ├── com/google/android/material/appbar/
│       │   │   │   └── FlingBehavior.java
│       │   │   ├── org/apache/commons/text/similarity/
│       │   │   │   └── FuzzyScore.java
│       │   │   └── org/schabi/newpipe/
│       │   │       ├── App.java / App.kt
│       │   │       ├── MainActivity.java
│       │   │       ├── RouterActivity.java
│       │   │       ├── NewPipeDatabase.java / .kt
│       │   │       ├── NewVersionWorker.kt
│       │   │       ├── SharedWebViewRuntime.java
│       │   │       ├── WebViewJavaScriptDecoder.java
│       │   │       ├── YoutubePlayerResponseCache.java
│       │   │       ├── (+ other root-level activity/utility files)
│       │   │       │
│       │   │       ├── about/            # AboutActivity, licenses, software components
│       │   │       ├── database/         # Room entities, DAOs, migrations
│       │   │       │   ├── feed/
│       │   │       │   ├── history/
│       │   │       │   ├── learning/
│       │   │       │   ├── playlist/
│       │   │       │   ├── sponsorblock/
│       │   │       │   ├── stream/
│       │   │       │   └── subscription/
│       │   │       ├── dearrow/          # DeArrowService
│       │   │       ├── download/         # DownloadActivity, DownloadDialog
│       │   │       ├── error/            # ErrorActivity, ErrorInfo, ReCaptcha
│       │   │       ├── fragments/        # UI fragments
│       │   │       │   ├── detail/       # VideoDetailFragment, DescriptionFragment
│       │   │       │   └── list/
│       │   │       │       ├── channel/
│       │   │       │       ├── comments/
│       │   │       │       ├── kiosk/
│       │   │       │       ├── playlist/
│       │   │       │       ├── search/
│       │   │       │       ├── sponsorblock/
│       │   │       │       └── videos/
│       │   │       ├── info_list/        # Adapters, item holders, dialog
│       │   │       │   ├── dialog/
│       │   │       │   └── holder/
│       │   │       ├── ktx/              # Kotlin extensions (Bitmap, Bundle, View…)
│       │   │       ├── learning/         # Learning mode, notes, session tracking
│       │   │       ├── local/            # Local data management
│       │   │       │   ├── bookmark/
│       │   │       │   ├── dialog/
│       │   │       │   ├── feed/
│       │   │       │   ├── history/
│       │   │       │   ├── holder/
│       │   │       │   ├── playlist/
│       │   │       │   ├── sponsorblock/
│       │   │       │   └── subscription/
│       │   │       ├── player/           # Media player
│       │   │       │   ├── bulletComments/
│       │   │       │   ├── datasource/   # SABR / HLS / DASH data sources
│       │   │       │   ├── event/
│       │   │       │   ├── gesture/
│       │   │       │   ├── helper/
│       │   │       │   ├── listeners/
│       │   │       │   ├── mediabrowser/
│       │   │       │   ├── mediaitem/
│       │   │       │   ├── mediasession/
│       │   │       │   ├── mediasource/
│       │   │       │   ├── notification/
│       │   │       │   ├── playback/
│       │   │       │   ├── playqueue/
│       │   │       │   ├── resolver/
│       │   │       │   ├── seekbarpreview/
│       │   │       │   └── ui/           # MainPlayerUi, PopupPlayerUi, BackgroundPlayerUi
│       │   │       ├── settings/         # All settings fragments & helpers
│       │   │       │   ├── custom/
│       │   │       │   ├── export/
│       │   │       │   ├── migration/
│       │   │       │   ├── notifications/
│       │   │       │   ├── preferencesearch/
│       │   │       │   └── tabs/
│       │   │       ├── sleep/            # SleepTimerService
│       │   │       ├── streams/          # Stream muxing / conversion utilities
│       │   │       │   └── io/
│       │   │       ├── util/             # General utilities
│       │   │       │   ├── debounce/
│       │   │       │   ├── external_communication/
│       │   │       │   ├── image/
│       │   │       │   ├── potoken/      # PoToken generation (WebView-based)
│       │   │       │   ├── service_display/
│       │   │       │   ├── text/
│       │   │       │   └── urlfinder/
│       │   │       ├── views/            # Custom UI views
│       │   │       │   └── player/       # FastSeek overlay, SecondsView
│       │   │       ├── youtube/          # YouTube-specific logic (SABR, attestation)
│       │   │       └── us/shandian/giga/ # Download engine
│       │   │           ├── get/          # Download missions, SABR/HLS downloaders
│       │   │           ├── hls/          # HLS manifest parsing & transfer
│       │   │           ├── io/           # File I/O helpers
│       │   │           ├── postprocessing/ # Muxers (MP4, WebM, OGG…)
│       │   │           ├── service/      # DownloadManagerService
│       │   │           └── ui/           # Download UI (adapter, fragment)
│       │   │
│       │   └── res/                      # Android resources
│       │       ├── animator/
│       │       ├── drawable/             # 180+ XML vector drawables
│       │       ├── drawable-{hdpi…xxxhdpi}/  # Raster drawables per density
│       │       ├── drawable-night/
│       │       ├── drawable-nodpi/       # Service placeholder images
│       │       ├── font/                 # lxgw_wenkai.ttf
│       │       ├── layout/               # Activity & fragment layouts
│       │       ├── layout-land/
│       │       ├── layout-large-land/
│       │       ├── menu/
│       │       ├── mipmap-{hdpi…xxxhdpi}/ # Launcher icons
│       │       ├── values/               # strings, colors, styles, attrs, dimens…
│       │       ├── values-{lang}/        # 100+ locale translations
│       │       └── xml/                  # Preference XML, provider paths
│       │
│       └── test/                         # JVM unit tests
│           ├── java/org/schabi/newpipe/
│           │   ├── database/playlist/
│           │   ├── error/
│           │   ├── ktx/
│           │   ├── local/playlist/ + subscription/
│           │   ├── player/playqueue/
│           │   ├── settings/ + tabs/
│           │   ├── streams/
│           │   └── util/ + external_communication/ + image/ + urlfinder/
│           └── resources/
│               ├── import_export_test.json
│               └── settings/             # Test DB/settings ZIP fixtures
│
├── assets/                               # Project-level design assets (SVG, PNG)
│   └── screenshots/
│
├── buildSrc/                             # Gradle build logic
│   └── src/main/kotlin/
│       ├── CheckDependenciesOrder.kt
│       └── ProjectConfig.kt
│
├── checkstyle/                           # Code style rules
│   ├── checkstyle.xml
│   └── suppressions.xml
│
├── config/
│   └── aboutlibraries/libraries/        # Library metadata for AboutLibraries
│
├── desktopApp/                           # JVM desktop target (Compose Multiplatform)
│   └── src/main/kotlin/net/newpipe/app/
│       └── Main.kt
│
├── doc/                                  # Documentation & translated READMEs
│   ├── github-workflows/
│   └── README.{lang}.md (20+ languages)
│
├── fastlane/                             # F-Droid / store metadata
│   └── metadata/android/
│       └── {lang}/                       # 80+ locales, each with:
│           ├── short_description.txt
│           ├── full_description.txt
│           └── changelogs/{version}.txt
│
├── ffmpeg/                               # Bundled FFmpegKit AAR
│   ├── build.gradle.kts
│   └── ffmpeg-kit.aar
│
├── gradle/
│   ├── libs.versions.toml                # Version catalog
│   └── wrapper/
│       └── gradle-wrapper.properties
│
├── iosApp/                               # iOS target (Kotlin Multiplatform)
│   ├── Configuration/Config.xcconfig
│   ├── iosApp/
│   │   ├── ContentView.swift
│   │   ├── iOSApp.swift
│   │   ├── Info.plist
│   │   └── Assets.xcassets/
│   └── iosApp.xcodeproj/
│
├── shared/                               # Kotlin Multiplatform shared module
│   ├── build.gradle.kts
│   └── src/
│       ├── androidMain/kotlin/net/newpipe/app/
│       │   ├── ComposeActivity.kt
│       │   ├── di/settings/
│       │   ├── extensions/
│       │   └── platform/                 # Android-specific implementations
│       ├── commonMain/
│       │   ├── composeResources/
│       │   │   ├── drawable/             # Shared icons
│       │   │   ├── files/LICENSES/       # License text files
│       │   │   └── values{-lang}/        # Shared string resources (100+ locales)
│       │   └── kotlin/net/newpipe/app/
│       │       ├── App.kt
│       │       ├── composable/           # Reusable Compose components
│       │       │   └── about/
│       │       ├── di/                   # Dependency injection (Koin)
│       │       │   ├── serialization/
│       │       │   └── settings/
│       │       ├── model/                # AboutLibraries, License, Link data models
│       │       ├── navigation/           # Navigation destinations & NavDisplay
│       │       ├── platform/             # Platform abstraction interfaces
│       │       ├── preview/              # Compose preview providers
│       │       ├── screen/
│       │       │   ├── about/            # AboutScreen, LicensePage, LicenseDialog
│       │       │   └── settings/         # SettingsHomeScreen
│       │       ├── theme/                # Colors, dimens, Material theme
│       │       └── viewmodel/
│       │           ├── about/
│       │           └── settings/
│       ├── commonTest/kotlin/            # Compose UI tests (shared)
│       ├── iosMain/kotlin/net/newpipe/app/
│       │   ├── MainViewController.kt
│       │   ├── di/settings/
│       │   └── platform/                 # iOS-specific implementations
│       └── jvmMain/kotlin/net/newpipe/app/
│           ├── di/settings/
│           └── platform/                 # JVM-specific implementations
│
└── tools/                                # Benchmark / test helper scripts
    ├── run-youtube-click-to-first-frame.sh
    └── run-youtube-playback-benchmark.sh
```
