package io.sentry.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.ListProperty
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSniffer

abstract class SentryAnimalSnifferExtension {
  abstract val ignoredClasses: ListProperty<String>
  abstract val excludedClasses: ListProperty<String>

  fun ignoreClasses(vararg classes: String) {
    ignoredClasses.addAll(*classes)
  }

  fun mainExcludes(vararg excludes: String) {
    excludedClasses.addAll(*excludes)
  }
}

/**
 * Checks the main code of the JVM modules that Android apps also use against the Android API 21
 * signature. The JVM API needs no signature: javac's release flag and Kotlin's -Xjdk-release
 * already limit main code to the published Java version, see JdkTargetConvention.
 */
class SentryAnimalSnifferAndroidPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("ru.vyarus.animalsniffer")

    val extension =
      project.extensions.create("sentryAnimalSniffer", SentryAnimalSnifferExtension::class.java)

    project.addSignatureDependency("gummy-bears-api21")

    project.tasks.named("animalsnifferMain", AnimalSniffer::class.java).configure {
      ignoreClasses = ignoreClasses + extension.ignoredClasses.get()
      exclude(extension.excludedClasses.get())
    }

    project.tasks.named("check").configure { dependsOn("animalsnifferMain") }
  }
}

private fun Project.addSignatureDependency(libraryName: String) {
  val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
  dependencies.add("signature", signatureNotation(libs.findLibrary(libraryName).get().get()))
}

private fun signatureNotation(dependency: MinimalExternalModuleDependency): String {
  val module = "${dependency.module.group}:${dependency.module.name}"
  return "$module:${dependency.versionConstraint.requiredVersion}@signature"
}
