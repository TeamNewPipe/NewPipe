# Service plugins

WizeStream can install external streaming-service implementations from HTTPS JSON
repositories. It supports the PipePipe plugin contract and a separate native WizeStream
contract. Each repository entry points to a signed `.ppp` archive containing the service's
compiled code. JSON supplies metadata and configuration; website-specific extraction is
implemented in that code.

This guide describes the plugin implementation in this source branch. Check the release
notes for availability in a published APK.

## Install and manage plugins

1. Open **Settings → Plugins** and enable **Allow plugins**.
2. Read the explanation of executable plugins and enable the feature if you trust the publishers
   whose code you intend to install.
3. Add an HTTPS URL pointing to a repository's `index.json`, or use the community-repository shortcut.
4. Choose an available plugin, confirm its repository, and install it.
5. Restart WizeStream when prompted. The service becomes available through normal service selection.

An example publisher repository for Twitch is:

```text
https://github.com/MohrJonas/TwitchPipePipePlugin/releases/download/repo/index.json
```

The [publisher repository](https://github.com/MohrJonas/TwitchPipePipePlugin) and
[community catalog](https://github.com/InfinityLoop1308/awesome-pipepipe-plugins) are maintained
outside WizeStream. Adding a repository is an explicit user action; WizeStream does not
silently subscribe to a publisher because a saved playlist references its service.

The included official PipePipe catalog lists several services that WizeStream already provides.
Those entries display **already built into WizeStream** and cannot be installed as replacements.
YouTube, SoundCloud, media.ccc.de, PeerTube, Bandcamp, BiliBili, NicoNico, BitChute, Rumble,
Odysee, and Vimeo keep their existing built-in identities.

The **Installed** section provides settings, updates, and uninstall actions. **Refresh repositories**
reloads available versions. Where multiple explicitly added repositories list the same plugin and
publisher, WizeStream displays the highest compatible version once. Repository order resolves equal
version numbers. A newer version with an unsupported API or Android requirement does not hide an
older compatible version.

Installation, updates, disabling, and uninstalling take effect after a process restart.
Saved subscriptions, history, and playlists are preserved when a plugin is unavailable.
Follow the missing-service message to restore its plugin; removing the package does not delete
the service's saved content.

Plugin packages, repository URLs, publisher pins, and profile-specific plugin settings currently
remain local to the device; full backups and Device Sync do not transfer those files. Reinstall
the same plugin and reconfigure its settings on the destination device to use restored service
records. A full backup can restore the master enable switch, but that does not install any code.

### Android support

External plugins require **Android 7/API 24 or newer**. WizeStream's built-in services remain
available on Android 6/API 23. A package can declare a higher `minAndroidSdk`; the catalog
and installer enforce that requirement.

PipePipe packages use a separate, reproducible API-24 Java utility runtime bundled by WizeStream.
This supplies their platform-stream helpers while the app retains its Android-6 runtime.
Shared extractor types, date/time values, and serializable collection wrappers remain owned by
the host. This compatibility path does not cover every Java library API: `LocalDate.datesUntil(...)`,
static methods on shared `java.time.chrono` interfaces, and custom implementations relying on
their default methods have different compiled signatures or locations. PipePipe packages using
those APIs need a native WizeStream build.

Native WizeStream plugins use the host's own API-23 DEX compiler and NIO desugaring profile,
so their SDK interfaces and date/time APIs match the app. Their manifest still requires Android 7
or newer. The compiler profile and the device requirement are separate: keep the template's
compiler settings and raise `minAndroidSdk` when a service needs a newer Android version.

One older published Twitch archive requires special handling: version code 4, SHA-256
`f44371de1ec69c32fbf37cd61f5ce00d2b676005233437b0d1fad3fdb1263681`, calls an Android API added
in Android 13/API 33. WizeStream blocks those exact bytes on older systems. The publisher's
version code 5 provides a fallback for older Android versions. If the community catalog still
lists version 4, add the publisher repository above to obtain its newer compatible release. This exception does not
change the minimum for other plugins.

### Profiles and optional authentication

Plugin packages and repository configuration belong to the device. A loaded service receives
preferences bound to the profile that created it. Manifest settings and optional login cookies
are stored separately for each profile. When plugins are loaded, changing profiles restarts
the app process so cached plugin configuration cannot carry into the next profile.

Plugins declaring cookie authentication expose an optional **Login cookies** setting and
**Clear login cookies** action. The setting explains the login URL and required cookie name.
Credentials are entered as a cookie header, remain hidden in the settings summary, and require
a restart after changes. This version does not share the app's WebView cookie store with plugins.
An account is not required for installing a plugin or for services that support public access.

Deleting a profile clears its plugin preferences. Objects left over from that profile cannot
recreate those preferences. Plugin code still runs in the app process with app permissions:
profile-aware APIs provide the supported storage behavior, not a sandbox for untrusted code.

## Package and repository format

| File | Role |
| --- | --- |
| `index.json` | Repository name and plugin entries, including download URLs, versions, requirements, SHA-256 digests, signatures, and public keys. |
| `.ppp` | ZIP containing the plugin's `classes.dex`, JVM `.class` files, resources, and root manifest. |
| `plugin.json` | Plugin identity, entry class, host/API requirement, optional settings and display metadata. |

Downloads and every redirect must use HTTPS. WizeStream verifies the complete package's
SHA-256 digest and an Ed25519 signature over the **raw 32 digest bytes**. The catalog stores
the signature and raw 32-byte public key as Base64. The installed package is made read-only
and its receipt is checked again before loading.

The publisher key is pinned at first installation. Updates must retain that key and use a higher
`versionCode`. Uninstalling preserves the pin, so it does not authorize a different publisher to
take over the same plugin ID. Publisher-key rotation is not supported by this implementation;
keep a secure backup of the original signing pair.

Duplicate prevention checks known official plugin identities and normalized built-in names before
installation. The runtime registry also rejects duplicate IDs, normalized names, and equivalent
base URLs. A plugin should add a distinct service instead of replacing a built-in provider.

## Choose an authoring contract

| | PipePipe-compatible plugin | Native WizeStream plugin |
| --- | --- | --- |
| Manifest `host` | Omitted, or `"pipepipe"` | Explicitly `"wizestream"` |
| Manifest `apiVersion` | `"1.0"` or `"1.1"` | `"2.0"` |
| Entry interface | `PipePipePlugin` | `WizeStreamPlugin` |
| Context | `PluginContext` | `WizeStreamPluginContext` |
| Extra host facilities | Shared plugin preferences | Shared preferences, host version, and a captured UUID profile ID |
| Starting point | [Official PipePipe template](https://github.com/InfinityLoop1308/PipePipePluginTemplate) | [WizeStream template](../plugin-template/README.md) |

Native API 2.0 is deliberately identified separately. A current PipePipe host rejects its major
version, and WizeStream requires the explicit native host declaration before treating API 2.0
as its own. An undeclared future PipePipe API 2.x is not accidentally accepted as WizeStream's API.

WizeStream's compatible contract preserves the PipePipe Java interfaces and extractor-facing
signatures. A package's declared API version is still only part of compatibility: the Android
version, shared library APIs, extractor behavior, and the author's implementation also matter.
Use an actual packaged plugin test instead of assuming a shared package name proves compatibility.

## Build a native WizeStream plugin

The [`plugin-template`](../plugin-template/README.md) directory is an independent Gradle project
written in Kotlin. Build the host SDK once, then build and sign the sample from the repository root:

```sh
./gradlew :app:pluginSdkJar
./gradlew -p plugin-template generateSigningKey repositoryIndex
```

The SDK is exported to `app/build/outputs/plugin-sdk/wizestream-plugin-api.jar`. The sample
package and index appear under `plugin-template/build/repo/`. The template uses Java 21,
Android D8 with the host's minimum API 23 compiler profile and NIO desugaring configuration. Its README
explains independent-project use and overriding SDK/key locations.

The sample **WizeStream Example** has a unique ID and an example-only base URL. It proves that
the project compiles, packages, signs, and can register a service. Its `TODO` extraction methods
do not implement a real website; replace them before publishing a usable plugin.

### Native manifest

The complete sample manifest is in
[`plugin-template/src/main/resources/plugin.json`](../plugin-template/src/main/resources/plugin.json).
Its core fields are:

```json
{
  "pluginId": "org.example.wizestream.demo",
  "name": "WizeStream Example",
  "versionCode": 1,
  "versionName": "1.0.0",
  "host": "wizestream",
  "apiVersion": "2.0",
  "minHostVersionCode": 1051000,
  "minAndroidSdk": 24,
  "requiredCapabilities": ["profile-preferences"],
  "author": "Your name",
  "entryClass": "org.example.wizestream.demo.ExamplePlugin"
}
```

`minHostVersionCode` in the sample identifies the development baseline. Before publishing,
set it to the first released WizeStream version that actually contains the API you need.
Version code alone does not add plugin support to an older APK.

`requiredCapabilities` lets a plugin reject a host that lacks a facility it relies on. Native
API 2.0 currently advertises `profile-preferences`. Unknown required capabilities, a higher
required host version, or a higher Android minimum cause rejection before code is loaded.
The generated catalog copies these requirements from the manifest; the installer checks that
catalog and signed package agree.

### Entry and service implementation

The public entry class must have a no-argument constructor and implement `WizeStreamPlugin`:

```kotlin
class ExamplePlugin : WizeStreamPlugin {
    override fun createService(
        serviceId: Int,
        context: WizeStreamPluginContext
    ): StreamingService = ExampleService(serviceId, context)
}
```

Pass `serviceId` unchanged to `StreamingService`. The returned service's name must match the
manifest. Keep `pluginId` stable across releases: it determines the persistent numeric service
identity used by saved content, independently of installation order.

Implement URL handling and whichever extractors the provider supports: streams, search, channels,
playlists, comments, and kiosks. Use the host downloader and result types. Advertise capabilities
accurately and return the documented unsupported results for unavailable features.

`WizeStreamPluginContext` provides `getHostVersionCode()`, `getHostVersionName()`, and
`getProfileId()` in addition to the inherited plugin identity and preferences. The profile ID
is an opaque UUID **string**, not a numeric database key. Use `getPreferences()` for persisted
settings and credentials; the host binds it to that profile.

String and Boolean settings declared in `plugin.json` are rendered by the host. The context
exposes their defaults and a change-listener interface. The settings screen also offers a restart
because some implementations cache their configuration. Optional icons are supported. Theme
color metadata is parsed, but this host retains WizeStream's own application theme.

### Shared libraries and packaging

Compile against the exported SDK and the library versions pinned in the template. The extractor,
Kotlin standard library, nanojson, jsoup, and commons-lang3 are supplied by the host. They are
`compileOnly` dependencies and must not be copied into the plugin's JAR or DEX. The packaging task
rejects host-class namespaces in the archive.

If additional libraries are needed, package and relocate them deliberately to avoid conflicts,
and account for their licenses. Internal WizeStream UI, database, and player implementation classes
are not part of the plugin SDK contract. Keep `pluginMinSdk` and desugaring settings matched to
the host SDK; use the manifest to declare a higher device requirement and test the produced package.

### Publish and update

Upload the generated `.ppp` and `index.json` to an HTTPS host. Relative package URLs in the
generated index resolve against the index location. Share that index URL with users.

For an update, keep the plugin identity and signing pair, increase `versionCode`, update the human
readable `versionName`, and regenerate the package/index. The template builds one plugin per
catalog; a larger catalog can combine signed entries while retaining each publisher's key and
signature. Do not publish ephemeral CI signing keys as a maintained repository identity.

## Validation and licensing

The host tests cover signed-package verification, metadata and compatibility rejection, duplicate
providers, stable IDs, profile isolation, and plugin-defined saved objects. Android fixture tests
load the publisher's signed Twitch package and a native package built from this template. These
tests exercise package/runtime integration without relying on a live website; live extraction
and playback need separate service-specific checks.

Test the minimum supported Android version and a minified release host. Debug builds alone may
hide missing classes caused by shrinking, Java APIs unavailable on older Android, or binary API
mismatches. Preserve the package that was tested and publish its generated digest/signature.

The integrated extractor and this template carry **GPL-3.0-or-later** notices. The SDK's inherited
source notices and the template's [`LICENSE`](../plugin-template/LICENSE) remain part of the
distribution. Preserve relevant notices and include the applicable licenses and corresponding
source when distributing GPL components. Dependency licenses must also be accounted for.

The API-24 compatibility runtime is generated from Google's `desugar_jdk_libs` 2.1.5.
Its upstream GPLv2 with Classpath Exception license and additional notice are bundled in
`assets/plugin-runtime/`, alongside the runtime configuration's license and build metadata.

The package format and original host contract are based on the released
[PipePipe extractor plugin implementation](https://github.com/InfinityLoop1308/PipePipeExtractor/tree/e9818f8ef16d3720107c51f6ee157aecb1da2b70/extractor/src/main/java/org/schabi/newpipe/extractor/plugin)
and the [official template](https://github.com/InfinityLoop1308/PipePipePluginTemplate/tree/91950887dfd173263ecacfc466437a33777d242d).
