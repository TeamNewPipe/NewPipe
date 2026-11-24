/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations

/** Builds the official desugared Java runtime for independently compiled API-24 plugins. */
@CacheableTask
abstract class GeneratePluginRuntime @Inject constructor(private val execOperations: ExecOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeLibraries: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val runtimeConfiguration: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val androidLibraries: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val compilerJar: RegularFileProperty

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @get:Input
    abstract val runtimeVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val scratch = temporaryDir.apply { mkdirs() }
        val configurationJar = runtimeConfiguration.singleFile
        val configurationFile = File(scratch, "desugar.json")
        val license = ZipFile(configurationJar).use { archive ->
            val entry = requireNotNull(archive.getEntry("META-INF/desugar/d8/desugar.json")) {
                "Desugared library configuration is missing desugar.json"
            }
            archive.getInputStream(entry).use { input -> configurationFile.outputStream().use { output -> input.copyTo(output) } }
            archive.getInputStream(requireNotNull(archive.getEntry("LICENSE"))).use { it.readBytes() }
        }
        val keepRules = File(scratch, "keep.pro").apply { writeText("-keep class j$.** { *; }\n") }
        val compiled = File(scratch, "runtime.zip").apply { delete() }
        val command = mutableListOf(
            "--release", "--min-api", "24", "--thread-count", "1",
            "--desugared-lib", configurationFile.absolutePath,
            "--pg-conf", keepRules.absolutePath,
            "--output", compiled.absolutePath
        )
        androidLibraries.files.sortedBy(File::getAbsolutePath).forEach {
            command += listOf("--lib", it.absolutePath)
        }
        // The configuration artifact also supplies conversion classes used by the runtime.
        command += (runtimeLibraries.files + configurationJar).sortedBy(File::getAbsolutePath).map(File::getAbsolutePath)
        execOperations.javaexec {
            executable = javaLauncher.get().executablePath.asFile.absolutePath
            classpath(compilerJar)
            mainClass.set("com.android.tools.r8.L8")
            maxHeapSize = "1536m"
            jvmArgs("-XX:ActiveProcessorCount=1", "-Dfile.encoding=UTF-8", "-Duser.language=en", "-Duser.country=US", "-Duser.timezone=UTC")
            args(command)
        }.assertNormalExitValue()

        val directory = outputDirectory.get().asFile.resolve("plugin-runtime").apply { mkdirs() }
        val packaged = directory.resolve("desugar-api24.zip")
        // Keep generated assets reproducible even if a compiler changes its ZIP timestamps.
        ZipFile(compiled).use { input ->
            val dexFiles = input.entries().asSequence().filter { it.name.matches(Regex("classes(?:[0-9]+)?\\.dex")) }.sortedBy { it.name }.toList()
            check(dexFiles.any { it.name == "classes.dex" }) { "L8 did not produce the plugin runtime" }
            ZipOutputStream(packaged.outputStream().buffered()).use { output ->
                dexFiles.forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name).apply { time = 0 })
                    input.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(packaged.readBytes()).joinToString("") { "%02x".format(it) }
        directory.resolve("desugar-api24.sha256").writeText("$hash\n")
        directory.resolve("configuration-LICENSE.txt").writeBytes(license)
        directory.resolve("runtime.txt").writeText(
            "com.android.tools:desugar_jdk_libs:${runtimeVersion.get()}\n" +
                "com.android.tools:desugar_jdk_libs_configuration:${runtimeVersion.get()}\n" +
                "Compiled by Android L8 for min-api 24; upstream sources: https://github.com/google/desugar_jdk_libs\n"
        )
    }
}
