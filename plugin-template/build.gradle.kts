/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Packaging format adapted from InfinityLoop1308/PipePipePluginTemplate
 * 91950887dfd173263ecacfc466437a33777d242d. Native SDK and JCA signing tasks are WizeStream additions.
 */
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    `java-library`
    kotlin("jvm") version "2.3.21"
}

repositories {
    google()
    // Match the host's CI route around Maven Central rate limits on shared runner IPs.
    if (System.getenv("GITHUB_ACTIONS") == "true") {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
    }
    mavenCentral()
    maven("https://jitpack.io")
}

kotlin {
    jvmToolchain(21)
}

val sdkJar = file(
    providers.gradleProperty("pluginSdkJar")
        .orElse("../app/build/outputs/plugin-sdk/wizestream-plugin-api.jar").get()
)
val desugarConfig by configurations.creating

dependencies {
    // The app supplies these classes. Keep them out of the plugin archive and DEX.
    compileOnly(files(sdkJar))
    compileOnly(kotlin("stdlib"))
    compileOnly("com.github.TeamNewPipe:nanojson:${property("nanojsonVersion")}")
    compileOnly("org.jsoup:jsoup:${property("jsoupVersion")}")
    compileOnly("org.apache.commons:commons-lang3:${property("lang3Version")}")
    compileOnly("com.github.spotbugs:spotbugs-annotations:${property("spotbugsVersion")}")
    desugarConfig("com.android.tools:desugar_jdk_libs_configuration_nio:${property("desugarJdkLibsVersion")}")
}

val manifestFile = file("src/main/resources/plugin.json")

@Suppress("UNCHECKED_CAST")
val pluginManifest = JsonSlurper().parse(manifestFile) as Map<String, Any?>
val pluginVersionCode = (pluginManifest["versionCode"] as Number).toInt()
require(pluginManifest["host"] == "wizestream" && pluginManifest["apiVersion"] == "2.0") {
    "This template targets native WizeStream API 2.0"
}
require(pluginVersionCode > 0) { "versionCode must be a positive integer" }

val verifySdk by tasks.registering {
    doLast {
        require(sdkJar.isFile) {
            "SDK jar missing: run ./gradlew :app:pluginSdkJar in the WizeStream root, " +
                "or supply -PpluginSdkJar=/absolute/path/wizestream-plugin-api.jar"
        }
    }
}
tasks.named("compileKotlin") { dependsOn(verifySdk) }
tasks.named("compileJava") { dependsOn(verifySdk) }

tasks.withType<Jar>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val extractDesugarConfig by tasks.registering(Copy::class) {
    from({ zipTree(desugarConfig.singleFile) }) {
        include("META-INF/desugar/d8/desugar.json")
        eachFile { path = name }
    }
    includeEmptyDirs = false
    into(layout.buildDirectory.dir("desugar"))
}

val pluginJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
val dexDirectory = layout.buildDirectory.dir("dex")
val pluginMinSdk = providers.gradleProperty("pluginMinSdk").map(String::toInt)
val pluginBuildToolsVersion = providers.gradleProperty("buildToolsVersion")
val pluginAndroidPlatform = providers.gradleProperty("androidPlatform")
val pluginRepositoryName = providers.gradleProperty("repositoryName")

val dex by tasks.registering(Exec::class) {
    dependsOn(tasks.named("jar"), extractDesugarConfig)
    inputs.file(pluginJar)
    inputs.files(configurations.compileClasspath)
    inputs.property("minSdk", pluginMinSdk)
    inputs.property("buildTools", pluginBuildToolsVersion)
    inputs.property("androidPlatform", pluginAndroidPlatform)
    inputs.file(layout.buildDirectory.file("desugar/desugar.json"))
    outputs.dir(dexDirectory)
    doFirst {
        require(pluginMinSdk.get() == 23) { "Native plugin DEX must match the host's API-23 compiler profile" }
        val local = Properties().apply {
            file("local.properties").takeIf(File::isFile)?.inputStream()?.use(::load)
        }
        val sdkRoot = local.getProperty("sdk.dir")
            ?: providers.environmentVariable("ANDROID_HOME").orNull
            ?: providers.environmentVariable("ANDROID_SDK_ROOT").orNull
            ?: error("Set sdk.dir in local.properties or ANDROID_HOME to your Android SDK")
        val d8Name = if (System.getProperty("os.name").startsWith("Windows")) "d8.bat" else "d8"
        val d8 = File(sdkRoot, "build-tools/${pluginBuildToolsVersion.get()}/$d8Name")
        val androidJar = File(sdkRoot, "platforms/${pluginAndroidPlatform.get()}/android.jar")
        require(d8.isFile && androidJar.isFile) {
            "Install Android ${pluginAndroidPlatform.get()} and build-tools ${pluginBuildToolsVersion.get()}"
        }
        val output = dexDirectory.get().asFile
        project.delete(output)
        output.mkdirs()
        val command = mutableListOf(
            d8.absolutePath, "--release", "--min-api", pluginMinSdk.get().toString(),
            "--lib", androidJar.absolutePath,
            "--desugared-lib", layout.buildDirectory.file("desugar/desugar.json").get().asFile.absolutePath,
            "--output", output.absolutePath
        )
        configurations.compileClasspath.get().files.sortedBy(File::getAbsolutePath).forEach {
            command += listOf("--classpath", it.absolutePath)
        }
        command += pluginJar.get().asFile.absolutePath
        commandLine(command)
    }
}

val pluginPackage by tasks.registering(Zip::class) {
    group = "plugin"
    description = "Build the native WizeStream .ppp without bundling host classes"
    dependsOn(dex)
    archiveFileName.set("${rootProject.name}-$pluginVersionCode.ppp")
    destinationDirectory.set(layout.buildDirectory.dir("repo"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from({ zipTree(pluginJar.get().asFile) }) {
        exclude("META-INF/MANIFEST.MF")
    }
    from(dexDirectory) { include("classes*.dex") }
    doLast {
        ZipFile(archiveFile.get().asFile).use { zip ->
            check(zip.getEntry("plugin.json") != null && zip.getEntry("classes.dex") != null)
            val forbidden = listOf(
                "org/schabi/newpipe/",
                "kotlin/",
                "org/jsoup/",
                "com/grack/nanojson/",
                "org/apache/commons/"
            )
            check(
                zip.entries().asSequence().none { entry ->
                    forbidden.any(entry.name::startsWith)
                }
            ) { "Plugin package includes host-provided classes" }
        }
    }
}

val privateKeyFile = file(providers.gradleProperty("pluginSigningKey").orElse("signing-key.pk8").get())
val publicKeyFile = file(providers.gradleProperty("pluginPublicKey").orElse("public-key.der").get())

val generateSigningKey by tasks.registering {
    group = "plugin"
    description = "Create one persistent Ed25519 key pair without replacing existing keys"
    doNotTrackState("Signing keys must never enter Gradle's build cache")
    doLast {
        if (privateKeyFile.exists() || publicKeyFile.exists()) {
            require(privateKeyFile.isFile && publicKeyFile.isFile) {
                "Incomplete signing key pair; restore its matching file from your backup"
            }
            logger.lifecycle("Reusing existing signing key files")
        } else {
            privateKeyFile.parentFile.mkdirs()
            publicKeyFile.parentFile.mkdirs()
            val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
            val attributes = if (privateKeyFile.toPath().fileSystem.supportedFileAttributeViews().contains("posix")) {
                arrayOf(PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
            } else {
                emptyArray()
            }
            Files.createFile(privateKeyFile.toPath(), *attributes)
            Files.write(privateKeyFile.toPath(), pair.private.encoded, StandardOpenOption.WRITE)
            Files.write(publicKeyFile.toPath(), pair.public.encoded, StandardOpenOption.CREATE_NEW)
            logger.lifecycle("Created signing key files; back them up before publishing")
        }
    }
}

tasks.register("repositoryIndex") {
    group = "plugin"
    description = "Sign the package and write build/repo/index.json"
    dependsOn(pluginPackage)
    mustRunAfter(generateSigningKey)
    doNotTrackState("Signing uses a private key and must not be cached")
    doLast {
        require(privateKeyFile.isFile && publicKeyFile.isFile) {
            "Run generateSigningKey once, then keep that key pair for all updates"
        }
        val factory = KeyFactory.getInstance("Ed25519")
        val privateKey = factory.generatePrivate(PKCS8EncodedKeySpec(privateKeyFile.readBytes()))
        val publicKey = factory.generatePublic(X509EncodedKeySpec(publicKeyFile.readBytes()))
        val encodedPublic = publicKey.encoded
        val ed25519Prefix = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03,
            0x2b, 0x65, 0x70, 0x03, 0x21, 0x00
        )
        require(
            encodedPublic.size == ed25519Prefix.size + 32 &&
                encodedPublic.copyOfRange(0, ed25519Prefix.size).contentEquals(ed25519Prefix)
        ) {
            "Public key must use the standard Ed25519 X.509 encoding"
        }
        val pkg = pluginPackage.get().archiveFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-256").digest(pkg.readBytes())
        val signature = Signature.getInstance("Ed25519").run {
            initSign(privateKey)
            update(digest) // Wire format signs the raw 32 digest bytes, never the hex text.
            sign()
        }
        check(
            Signature.getInstance("Ed25519").run {
                initVerify(publicKey)
                update(digest)
                verify(signature)
            }
        ) { "The public and private signing keys do not match" }
        val entry = linkedMapOf<String, Any?>()
        listOf(
            "pluginId", "name", "versionCode", "versionName", "host", "apiVersion", "author",
            "description", "minHostVersionCode", "minAndroidSdk", "requiredCapabilities"
        ).forEach { key ->
            if (pluginManifest.containsKey(key)) entry[key] = pluginManifest[key]
        }
        entry["package"] = pkg.name
        entry["sha256"] = digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
        entry["signature"] = Base64.getEncoder().encodeToString(signature)
        entry["publicKey"] = Base64.getEncoder().encodeToString(encodedPublic.takeLast(32).toByteArray())
        entry["nsfw"] = false
        val index = linkedMapOf("formatVersion" to 1, "name" to pluginRepositoryName.get(), "plugins" to listOf(entry))
        val destination = layout.buildDirectory.file("repo/index.json").get().asFile
        destination.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(index)) + "\n")
        logger.lifecycle("Wrote ${pkg.name} and index.json to build/repo")
    }
}
