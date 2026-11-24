# Native WizeStream plugin template

This is a standalone Gradle project for **WizeStream plugin API 2.0**. It builds a signed
`.ppp` package and an `index.json` catalog. The sample registers **WizeStream Example** with
a reserved example URL; its extraction methods are intentionally unfinished.

For installation, compatibility, profile behavior, and publication guidance, read
[the plugin guide](../docs/plugins.md).

## Build in the WizeStream checkout

Use JDK 21 and the Android SDK versions in `gradle.properties`. The parent repository's
Gradle wrapper supplies the supported Gradle version.

```sh
# Run these commands from the WizeStream repository root.
./gradlew :app:pluginSdkJar
./gradlew -p plugin-template generateSigningKey repositoryIndex
```

The output is:

- `build/repo/wizestream-example-1.ppp`
- `build/repo/index.json`

`generateSigningKey` creates `signing-key.pk8` and `public-key.der` in this project on the
first run, and reuses them on later runs. Back up both files before publishing. They are
excluded from Git. The private key is PKCS#8 DER; the public file is X.509 DER. The index
contains the raw public key in the format the host expects.

To build a subsequent version with the existing key, update the manifest's `versionCode`
and `versionName`, then run:

```sh
./gradlew -p plugin-template repositoryIndex
```

The package is deterministic for the same source and build inputs; a repository's signatures
also depend on its persistent signing key. The build signs the raw SHA-256 digest bytes with
Ed25519 and verifies its own signature before writing the index.

## Use as a separate project

Copy this directory, preserving `LICENSE`. Supply a previously exported SDK JAR and use
Gradle 9.5.1, or copy the parent repository's Gradle wrapper files (`gradlew`, `gradlew.bat`,
and `gradle/wrapper/`) into the copied project.

```sh
gradle -PpluginSdkJar=/absolute/path/wizestream-plugin-api.jar \
  generateSigningKey repositoryIndex
```

Set `ANDROID_HOME`, or put `sdk.dir=/absolute/path/android-sdk` in a local, untracked
`local.properties`. Install the platform and build tools named in `gradle.properties`.
Use `-PpluginSigningKey=/private/path/key.pk8` and
`-PpluginPublicKey=/private/path/public.der` if keys are stored elsewhere.

## Replace the sample

1. Choose a permanent, unique `pluginId` and a service name that WizeStream does not already provide.
2. Rename `ExamplePlugin` and `ExampleService`, and update `entryClass` and `name` in `plugin.json`.
3. Implement the URL handlers and extractors marked `TODO`. Advertise only supported capabilities.
4. Set the actual author, description, minimum host version, and any optional manifest settings.
5. Test on the minimum Android version and against a release build of the host before publishing.

The manifest keeps `host: "wizestream"`, `apiVersion: "2.0"`, and `minAndroidSdk: 24`.
Its `minHostVersionCode: 1052000` requires WizeStream **1.52.0 or newer**, the first release
with native plugin support. Raise this minimum if your plugin uses host APIs added in a
later release.

Native plugin DEX uses `pluginMinSdk=23` and the host's `desugar_jdk_libs_configuration_nio`
profile. This matches WizeStream's compiled SDK interfaces and date/time APIs. It is separate
from the manifest's Android-7 device requirement. Keep the compiler profile unchanged; if a
service needs a newer Android version, raise `minAndroidSdk` in its manifest instead.

The SDK, Kotlin standard library, nanojson, jsoup, and commons-lang3 are `compileOnly`.
The host provides them. Do not package replacement copies or import internal activities,
database classes, or player implementation classes. A plugin should use the public extractor
and plugin contracts.

The packaging layout is adapted from
[PipePipePluginTemplate at 91950887](https://github.com/InfinityLoop1308/PipePipePluginTemplate/tree/91950887dfd173263ecacfc466437a33777d242d).
This template and the integrated extractor SDK are GPL-3.0-or-later; see `LICENSE` and preserve
the source notices when adapting them.
