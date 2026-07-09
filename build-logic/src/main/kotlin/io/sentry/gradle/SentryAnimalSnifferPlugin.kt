package io.sentry.gradle

import javax.inject.Inject
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.SourceTask

abstract class SentryAnimalSnifferExtension @Inject constructor(objects: ObjectFactory) {
  val ignoredClasses: ListProperty<String> = objects.listProperty(String::class.java)
  val mainExcludes: ListProperty<String> = objects.listProperty(String::class.java)
}

class SentryAnimalSnifferPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply("ru.vyarus.animalsniffer")

    val libs = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    val extension =
      project.extensions.create(
        "sentryAnimalSniffer",
        SentryAnimalSnifferExtension::class.java,
      )

    val java18Version = libs.findVersion("java18Signature").get().requiredVersion
    project.dependencies.add(
      "signature",
      "org.codehaus.mojo.signature:java18:$java18Version@signature",
    )

    project.tasks.matching { it.name == "check" }.configureEach {
      dependsOn("animalsnifferMain")
    }

    project.afterEvaluate {
      project.tasks.named("animalsnifferMain", SourceTask::class.java).configure {
        exclude(extension.mainExcludes.get())
        val ignoredClasses = extension.ignoredClasses.get()
        if (ignoredClasses.isNotEmpty()) {
          javaClass.getMethod("setIgnoreClasses", Iterable::class.java).invoke(this, ignoredClasses)
        }
      }
    }
  }
}

class SentryAnimalSnifferAndroidPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.pluginManager.apply(SentryAnimalSnifferPlugin::class.java)

    val libs = project.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")
    val gummyBears = libs.findLibrary("gummy-bears-api21").get().get()
    val gummyBearsVersion = libs.findVersion("gummyBears").get().requiredVersion
    project.dependencies.add(
      "signature",
      "${gummyBears.module.group}:${gummyBears.module.name}:$gummyBearsVersion@signature",
    )
  }
}
