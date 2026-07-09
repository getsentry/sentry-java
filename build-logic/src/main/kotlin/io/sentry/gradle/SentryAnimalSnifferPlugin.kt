package io.sentry.gradle

import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import ru.vyarus.gradle.plugin.animalsniffer.AnimalSniffer

abstract class SentryAnimalSnifferExtension @Inject constructor(objects: ObjectFactory) {
  val ignoredClasses: ListProperty<String> = objects.listProperty(String::class.java)
  val mainExcludes: ListProperty<String> = objects.listProperty(String::class.java)
}

class SentryAnimalSnifferPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("ru.vyarus.animalsniffer")

    val extension =
      project.extensions.create(
        "sentryAnimalSniffer",
        SentryAnimalSnifferExtension::class.java,
      )

    project.addSignatureDependency("java18-signature")

    project.tasks.matching { it.name == "check" }.configureEach {
      dependsOn("animalsnifferMain")
    }

    project.afterEvaluate {
      project.tasks.named("animalsnifferMain", AnimalSniffer::class.java).configure {
        exclude(extension.mainExcludes.get())
        val ignoredClasses = extension.ignoredClasses.get()
        if (ignoredClasses.isNotEmpty()) {
          setIgnoreClasses(ignoredClasses)
        }
      }
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
