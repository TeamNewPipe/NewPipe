/*
 * SPDX-FileCopyrightText: 2024 NewPipe contributors <https://newpipe.net>
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

tasks.register("checkDependenciesOrder") {
    group = "verification"
    description = "Checks that each section in libs.versions.toml is sorted alphabetically"

    val tomlFile = file("../gradle/libs.versions.toml")

    doLast {
        if (!tomlFile.exists()) {
            throw GradleException("TOML file not found")
        }

        val lines = tomlFile.readLines()
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
            throw GradleException(
                "The following lines were not sorted:\n" +
                        nonSortedBlocks.joinToString("\n\n") { it.joinToString("\n") }
            )
        }
    }
}
