package io.sentry.gradle

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.kotlin.dsl.create
import org.jetbrains.kotlin.gradle.dsl.abi.AbiValidationExtension
import org.jetbrains.kotlin.gradle.dsl.abi.AbiValidationMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

private val KOTLIN_PLUGIN_IDS =
  listOf(
    "org.jetbrains.kotlin.jvm",
    "org.jetbrains.kotlin.android",
    "org.jetbrains.kotlin.multiplatform",
  )

/**
 * Checks the public ABI of a module against the dump checked into its `api` directory, using the
 * Kotlin Gradle plugin's own ABI validation. Adds `checkKotlinAbi`, which `check` depends on, and
 * `updateKotlinAbi`, which rewrites the dump.
 */
@OptIn(ExperimentalAbiValidation::class)
class SentryApiValidationPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    val extension = project.extensions.create<ApiValidationExtension>("sentryApiValidation")

    // The abiValidation extension only exists once a Kotlin plugin has been applied, and a module
    // may list this plugin before that one.
    KOTLIN_PLUGIN_IDS.forEach {
      project.pluginManager.withPlugin(it) { enable(project, extension) }
    }
  }

  private fun enable(project: Project, extension: ApiValidationExtension) {
    val kotlin = project.extensions.getByName("kotlin") as ExtensionAware
    when (val abi = kotlin.extensions.getByName("abiValidation")) {
      is AbiValidationMultiplatformExtension -> abi.also { it.enabled.set(true) }
      is AbiValidationExtension -> abi.also { it.enabled.set(true) }
      else -> throw GradleException("Unexpected abiValidation extension: ${abi.javaClass.name}")
    }.filters {
      // Lazy, because the module configures the extension after this plugin has been applied.
      exclude { byNames.addAll(extension.ignoredPackages.map { it.map { pkg -> "$pkg.**" } }) }
    }
  }
}
