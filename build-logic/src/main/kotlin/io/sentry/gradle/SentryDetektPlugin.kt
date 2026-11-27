package io.sentry.gradle

import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Runs detekt on a module against the shared ruleset, with a per-module baseline. */
class SentryDetektPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("io.gitlab.arturbosch.detekt")

    project.extensions.configure<DetektExtension> {
      buildUponDefaultConfig = true
      allRules = true
      config.setFrom(project.isolated.rootProject.projectDirectory.file("detekt.yml"))
      baseline = project.file("detekt-baseline.xml")
    }
  }
}
