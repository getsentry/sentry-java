package io.sentry.gradle

import com.android.build.gradle.BaseExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** The JDK every module compiles and tests with, whatever JDK runs Gradle. */
private val BUILD_JDK = JavaLanguageVersion.of(21)

/** The level the SDK publishes, so that Java 8 consumers can still use it. */
private val PUBLISHED_JAVA_VERSION = JavaVersion.VERSION_1_8

/**
 * Holds every toolchain, bytecode level decision and compilation option of a module. It does the
 * following
 * - sample apps target the build JDK (21)
 * - unit test code will target the build JDK (21) (required for Mockito 5)
 * - published JVM modules main code pins the Java 1.8 release
 * - published Android modules main code targets Java 1.8
 * - minSdk is set for Android libraries
 */
class SentryCompilationPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.plugins.withType(JavaBasePlugin::class.java) {
      project.extensions.getByType(JavaPluginExtension::class.java).toolchain {
        languageVersion.set(BUILD_JDK)
      }
    }

    // Only works for non-android modules
    // See https://issuetracker.google.com/issues/278800528
    project.plugins.withId("com.vanniktech.maven.publish") {
      project.plugins.withId("java-library") {
        project.tasks.named("compileJava", JavaCompile::class.java) {
          options.release.set(PUBLISHED_JAVA_VERSION.majorVersion.toInt())
        }
      }
    }

    project.plugins.withId("com.android.library") {
      val android = project.extensions.getByType(BaseExtension::class.java)
      android.compileOptions {
        sourceCompatibility = PUBLISHED_JAVA_VERSION
        targetCompatibility = PUBLISHED_JAVA_VERSION
      }
      android.defaultConfig { aarMetadata { minCompileSdk = project.minSdk() } }
    }

    listOf("com.android.library", "com.android.application").forEach { pluginId ->
      project.plugins.withId(pluginId) { project.targetBuildJdkForUnitTests() }
    }
  }

  private fun Project.minSdk(): Int =
    extensions
      .getByType(VersionCatalogsExtension::class.java)
      .named("libs")
      .findVersion("minSdk")
      .orElseThrow { IllegalStateException("libs.versions.minSdk is missing") }
      .requiredVersion
      .toInt()

  private fun Project.targetBuildJdkForUnitTests() {
    // AGP assigns these task properties when it creates the compile tasks, as they can be
    // variant aware, so right now there's no way around using afterEvaluate
    afterEvaluate {
      val unitTestTask = { task: Task -> task.name.contains("UnitTest") }
      tasks.withType(JavaCompile::class.java).matching(unitTestTask).configureEach {
        sourceCompatibility = BUILD_JDK.toString()
        targetCompatibility = BUILD_JDK.toString()
      }
      tasks.withType(KotlinJvmCompile::class.java).matching(unitTestTask).configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.fromTarget(BUILD_JDK.toString()))
      }
    }
  }
}
