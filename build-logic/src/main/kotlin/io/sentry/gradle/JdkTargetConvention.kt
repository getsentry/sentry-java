package io.sentry.gradle

import com.android.build.gradle.BaseExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget
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
 * - published JVM modules main code pins the Java 1.8 release, for Java and for Kotlin
 * - published Android modules main code targets Java 1.8
 * - minSdk is set for Android libraries
 */
class JdkTargetConvention : Plugin<Project> {
  override fun apply(project: Project) {
    project.plugins.withType(JavaBasePlugin::class.java) {
      project.extensions.getByType(JavaPluginExtension::class.java).toolchain {
        languageVersion.set(BUILD_JDK)
      }
    }

    project.plugins.withId("com.vanniktech.maven.publish") {
      // Only works for non-android modules
      // See https://issuetracker.google.com/issues/278800528
      project.plugins.withId("java-library") {
        project.tasks.named("compileJava", JavaCompile::class.java) {
          options.release.set(PUBLISHED_JAVA_VERSION.majorVersion.toInt())
        }
      }
      // -Xjdk-release is the Kotlin counterpart of release: without it Kotlin resolves against the
      // build JDK and happily compiles e.g. Files.readString() into Java 8 bytecode. The version
      // follows jvmTarget, which the modules that publish a higher level set themselves.
      project.plugins.withId("org.jetbrains.kotlin.jvm") {
        project.tasks.named("compileKotlin", KotlinJvmCompile::class.java) {
          compilerOptions.freeCompilerArgs.add(
            compilerOptions.jvmTarget.map { "-Xjdk-release=${it.target}" }
          )
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

  /**
   * Mockito 5 and mockito-kotlin 6 are built for Java 11, so test code - which is never published -
   * cannot share the Java 8 target of the main code. AGP has no per variant compileOptions, so this
   * goes through the Kotlin compilations: their compile task providers are configured after AGP
   * wrote its own compileOptions, which an apply time configureEach would lose against.
   */
  private fun Project.targetBuildJdkForUnitTests() {
    plugins.withId("org.jetbrains.kotlin.android") {
      extensions.getByType(KotlinAndroidProjectExtension::class.java).target.targetBuildJdk()
    }
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
      extensions
        .getByType(KotlinMultiplatformExtension::class.java)
        .targets
        .withType(KotlinAndroidTarget::class.java)
        .configureEach { targetBuildJdk() }
    }
  }

  private fun KotlinAndroidTarget.targetBuildJdk() {
    compilations.configureEach {
      if (name.endsWith("UnitTest")) {
        compileTaskProvider.configure {
          compilerOptions.jvmTarget.set(JvmTarget.fromTarget(BUILD_JDK.toString()))
        }
        compileJavaTaskProvider?.configure {
          sourceCompatibility = BUILD_JDK.toString()
          targetCompatibility = BUILD_JDK.toString()
        }
      }
    }
  }
}
