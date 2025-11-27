/*
 * SPDX-FileCopyrightText: 2025 NewPipe e.V. <https://newpipe-ev.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import me.champeau.gradle.igp.gitRepositories
import org.eclipse.jgit.api.Git
import java.io.FileInputStream
import java.util.Properties

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    // need to manually read version catalog because it is not available in settings.gradle.kts
    // this code is duplicate with the below but there is no way to avoid it...
    fun findInVersionCatalog(versionIdentifier: String): String {
        val regex = "^.*$versionIdentifier *= *\"([^\"]+)\".*$".toRegex()
        return File("gradle/libs.versions.toml")
            .readLines()
            .firstNotNullOf { regex.find(it)?.groupValues?.get(1) }
    }

    id("me.champeau.includegit") version findInVersionCatalog("includegitPlugin")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven(url = "https://repo.clojars.org")
    }
}
include (":app")


// All of the code below handles depending on libraries from git repos, in particular
// NewPipeExtractor. The git commit to checkout can be updated in libs.versions.toml.
// If you want to use a local copy of NewPipeExtractor (provided that you have cloned it in
// `../NewPipeExtractor`), you can add `useLocalNewPipeExtractor=true` to `local.properties`.
// Or you use a commit you pushed to GitHub by just replacing TeamNewPipe with your GitHub
// name below here and update the commit hash in libs.versions.toml with the commit hash of the
// (pushed) commit you want to test.

data class IncludeGitRepo(
    val name: String,
    val uri: String,
    val projectPath: String,
    val commit: String,
)

// need to manually read version catalog because it is not available in settings.gradle.kts
// this code is duplicate with the above but there is no way to avoid it...
fun findInVersionCatalog(versionIdentifier: String): String {
    val regex = "^.*$versionIdentifier *= *\"([^\"]+)\".*$".toRegex()
    return File("gradle/libs.versions.toml")
        .readLines()
        .firstNotNullOf { regex.find(it)?.groupValues?.get(1) }
}

val newPipeExtractor = IncludeGitRepo(
    name = "NewPipeExtractor",
    uri = "https://github.com/TeamNewPipe/NewPipeExtractor",
    projectPath = ":extractor",
    commit = findInVersionCatalog("teamnewpipe-newpipeextractor"),
)

val localProperties = Properties().apply {
    try {
        load(FileInputStream(File(rootDir, "local.properties")))
    } catch (e: Throwable) {
        println("Warning: can't read local.properties: $e")
    }
}

if (localProperties.getOrDefault("useLocalNewPipeExtractor", "") == "true") {
    includeBuild("../${newPipeExtractor.name}") {
        dependencySubstitution {
            substitute(module("git.included.build:${newPipeExtractor.name}"))
                .using(project(newPipeExtractor.projectPath))
        }
    }

} else {
    // if the repo has already been cloned, the gitRepositories plugin is buggy and doesn't
    // fetch the remote repo before trying to checkout the commit (in case the commit has changed),
    // and doesn't clone the repo again if the remote changed, so we need to do it manually
    val repo = newPipeExtractor
    val file = File("$rootDir/checkouts/${repo.name}")
    if (file.isDirectory) {
        val git = Git.open(file)
        val sameRemote = git.remoteList().call()
            .any { rem -> rem.urIs.any { uri -> uri.toString() == repo.uri } }
        if (sameRemote) {
            // the commit may have changed, fetch again
            git.fetch().call()
        } else {
            // the remote changed, delete the repository and start from scratch
            println("Git: remote for ${repo.name} changed, deleting the current folder")
            file.deleteRecursively()
        }
    }

    gitRepositories {
        include(repo.name) {
            uri.set(repo.uri)
            commit.set(repo.commit)
            autoInclude.set(false)
            includeBuild("") {
                dependencySubstitution {
                    substitute(module("git.included.build:${repo.name}"))
                        .using(project(repo.projectPath))
                }
            }
        }
    }
}
