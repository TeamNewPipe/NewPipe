/*
 * SPDX-FileCopyrightText: 2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

abstract class CheckDependenciesOrder : DefaultTask() {

    @get:InputFile
    abstract val tomlFile: RegularFileProperty

    init {
        group = "verification"
        description = "Checks that each section in libs.versions.toml is sorted alphabetically"
    }

    @TaskAction
    fun run() {
        val file = tomlFile.get().asFile
        if (!file.exists()) error("TOML file not found")

        val lines = file.readLines()
        val nonSortedBlocks = mutableListOf<List<String>>()
        var currentBlock = mutableListOf<String>()
        var prevLine = ""
        var prevIndex = 0

        lines.forEachIndexed { lineIndex, line ->
            if (line.trim().isNotEmpty() && !line.startsWith("#")) {
                if (line.startsWith("[")) {
                    prevLine = ""
                } else {
                    val currIndex = lineIndex + 1
                    if (prevLine > line) {
                        if (currentBlock.isNotEmpty() && currentBlock.last() == "$prevIndex: $prevLine") {
                            currentBlock.add("$currIndex: $line")
                        } else {
                            if (currentBlock.isNotEmpty()) {
                                nonSortedBlocks.add(currentBlock)
                                currentBlock = mutableListOf()
                            }
                            currentBlock.add("$prevIndex: $prevLine")
                            currentBlock.add("$currIndex: $line")
                        }
                    }
                    prevLine = line
                    prevIndex = lineIndex + 1
                }
            }
        }

        if (currentBlock.isNotEmpty()) {
            nonSortedBlocks.add(currentBlock)
        }

        if (nonSortedBlocks.isNotEmpty()) {
            error(
                "The following lines were not sorted:\n" +
                        nonSortedBlocks.joinToString("\n\n") { it.joinToString("\n") }
            )
        }
    }
}
