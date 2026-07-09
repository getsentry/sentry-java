package io.sentry.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSniffer

open class SentryAnimalSnifferExtension(private val project: Project) {
  fun ignoreClasses(vararg classes: String) {
    project.tasks.named("animalsnifferMain", AnimalSniffer::class.java).configure {
      val ignoredClasses = getIgnoreClasses().toMutableList()
      ignoredClasses.addAll(classes)
      setIgnoreClasses(ignoredClasses)
    }
  }

  fun mainExcludes(vararg excludes: String) {
    project.tasks.named("animalsnifferMain", AnimalSniffer::class.java).configure {
      exclude(*excludes)
    }
  }
}

class SentryAnimalSnifferPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("ru.vyarus.animalsniffer")

    project.extensions.create(
      "sentryAnimalSniffer",
      SentryAnimalSnifferExtension::class.java,
      project,
    )

    project.addSignatureDependency("java18-signature")

    project.tasks.matching { it.name == "check" }.configureEach {
      dependsOn("animalsnifferMain")
    }

  }
}

class SentryAnimalSnifferAndroidPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(SentryAnimalSnifferPlugin::class.java)

    project.addSignatureDependency("gummy-bears-api21")
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
