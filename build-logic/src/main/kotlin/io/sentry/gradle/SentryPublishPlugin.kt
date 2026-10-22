package io.sentry.gradle

import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.distribution.DistributionContainer
import org.gradle.api.distribution.plugins.DistributionPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.kotlin.dsl.configure

/** Publishes a JVM or Android module and packages it as the zip distribution craft consumes. */
class SentryPublishPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.configurePublishing(DistributionContainer::configureForJvm)
  }
}

/**
 * Publishes a Kotlin Multiplatform module. Separate from [SentryPublishPlugin] because the
 * per-target distributions it lays out have no counterpart in a single-target module.
 */
class SentryMultiplatformPublishPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.configurePublishing(DistributionContainer::configureForMultiplatform)
  }
}

private fun Project.configurePublishing(
  configureDistributions: DistributionContainer.(Project) -> Unit
) {
  pluginManager.apply(DistributionPlugin::class.java)
  pluginManager.apply("com.vanniktech.maven.publish")

  extensions.configure<DistributionContainer> {
    configureDistributions(project)
    // craft only uses zip archives
    forEach { dist ->
      if (dist.name == DistributionPlugin.MAIN_DISTRIBUTION_NAME) {
        tasks.named("distTar").configure { enabled = false }
      } else {
        tasks.named(dist.name + "DistTar").configure { enabled = false }
      }
    }
  }

  val sep = File.separator
  val distributionFile =
    layout.buildDirectory.file("distributions$sep$name-$version.zip").get().asFile

  tasks.named("distZip").configure {
    dependsOn("publishToMavenLocal")
    val file = distributionFile
    doLast {
      if (!file.exists()) {
        throw IllegalStateException("Distribution file: ${file.absolutePath} does not exist")
      }
      if (file.length() == 0L) {
        throw IllegalStateException("Distribution file: ${file.absolutePath} is empty")
      }
    }
  }

  extensions.configure<MavenPublishBaseExtension> { assignAarTypes() }

  plugins.withId("java-library") {
    extensions.configure<MavenPublishBaseExtension> {
      // we have to disable javadoc publication in maven-publish plugin as it's not
      // including it in the .module file
      // https://github.com/vanniktech/gradle-maven-publish-plugin/issues/861
      // and do it ourselves
      configure(JavaLibrary(JavadocJar.None(), sourcesJar = true))
    }

    extensions.configure<JavaPluginExtension> { withJavadocJar() }
  }

  // this is needed for sentry-unity to consume our artifacts locally as proper maven publication
  extensions.configure<PublishingExtension> {
    repositories.maven {
      name = "unityMaven"
      url = isolated.rootProject.projectDirectory.dir("build${sep}unityMaven").asFile.toURI()
    }
  }
}
