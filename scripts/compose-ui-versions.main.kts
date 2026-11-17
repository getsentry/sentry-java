#!/usr/bin/env kotlin
@file:DependsOn("io.github.z4kn4fein:semver-jvm:3.1.0")

// Prints the androidx.compose.ui versions that the Compose UI matrix workflow runs the
// sentry-android-replay unit tests against: the latest stable, plus the latest pre-release when
// it is newer. Same approach as the AGP matrix in getsentry/sentry-android-gradle-plugin.

import io.github.z4kn4fein.semver.Version
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

val metadataUrl =
  "https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui/maven-metadata.xml"

val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(metadataUrl)
val versionNodes = document.documentElement.getElementsByTagName("version")
val versions =
  (0 until versionNodes.length).map { Version.parse(versionNodes.item(it).textContent, strict = false) }

val latestStable = versions.filter { it.isStable }.max()
val latestPreRelease = versions.filter { it.isPreRelease }.max()

val matrix = buildList {
  add(latestStable)
  if (latestPreRelease > latestStable) add(latestPreRelease)
}
val json = matrix.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }

println(json)
val githubOutput = System.getenv("GITHUB_OUTPUT")
if (githubOutput != null) File(githubOutput).appendText("versions=$json\n")
