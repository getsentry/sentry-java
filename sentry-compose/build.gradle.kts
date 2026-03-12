import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  alias(libs.plugins.kotlin.multiplatform)
  alias(libs.plugins.kotlin.compose)
  id("com.android.library")
  alias(libs.plugins.kover)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.detekt)
  alias(libs.plugins.dokka)
  alias(libs.plugins.dokka.javadoc)
  `maven-publish` // necessary for publishMavenLocal task to publish correct artifacts
}

kotlin {
  explicitApi()

  androidTarget {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_1_8)
      apiVersion.set(KotlinVersion.KOTLIN_1_9)
      languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
    publishLibraryVariants("release")
  }
  jvm("desktop") {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_1_8)
      apiVersion.set(KotlinVersion.KOTLIN_1_9)
      languageVersion.set(KotlinVersion.KOTLIN_1_9)
    }
  }

  coreLibrariesVersion = "1.8"

  sourceSets.all {
    // Allow all experimental APIs, since MPP projects are themselves experimental
    languageSettings.apply {
      optIn("kotlin.Experimental")
      optIn("kotlin.ExperimentalMultiplatform")
    }
  }

  sourceSets {
    val commonMain by getting {
      compilerOptions {
        apiVersion.set(KotlinVersion.KOTLIN_1_9)
        languageVersion.set(KotlinVersion.KOTLIN_1_9)
      }
    }
    val androidMain by getting {
      dependencies {
        api(projects.sentry)
        api(projects.sentryAndroidNavigation)

        compileOnly(libs.androidx.compose.material3)
        compileOnly(libs.androidx.navigation.compose)
        implementation(libs.androidx.lifecycle.common.java8)
      }
    }
    val androidUnitTest by getting {
      dependencies {
        implementation(libs.androidx.compose.ui.test.junit4)
        implementation(libs.androidx.navigation.compose)
        implementation(libs.androidx.test.ext.junit)
        implementation(libs.androidx.test.rules)
        implementation(libs.androidx.test.runner)
        implementation(libs.kotlin.test.junit)
        implementation(libs.mockito.inline)
        implementation(libs.mockito.kotlin)
        implementation(libs.roboelectric)
      }
    }
  }
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.compose"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    // for AGP 4.1
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
  }

  sourceSets["main"].apply { manifest.srcFile("src/androidMain/AndroidManifest.xml") }

  buildTypes {
    getByName("debug") { consumerProguardFiles("proguard-rules.pro") }
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
  }

  testOptions {
    animationsDisabled = true
    unitTests.apply {
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
    }
  }

  lint {
    warningsAsErrors = true
    checkDependencies = true

    // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
    checkReleaseBuilds = false
  }

  buildFeatures { buildConfig = true }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

// Compile Compose110Helper.kt against Compose 1.10 where internal LayoutNode accessors
// are mangled with module name "ui" (e.g. getChildren$ui()) instead of "ui_release"
val compose110Classpath by
  configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
      attribute(Attribute.of("artifactType", String::class.java), "android-classes-jar")
    }
  }

val compose110KotlinCompiler by
  configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
  }

dependencies {
  //noinspection UseTomlInstead
  compose110Classpath("androidx.compose.ui:ui-android:1.10.0")
  //noinspection UseTomlInstead
  compose110KotlinCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.0")
}

val compileCompose110 by
  tasks.registering(JavaExec::class) {
    val sourceDir = file("src/compose110/kotlin")
    val outputDir = layout.buildDirectory.dir("classes/kotlin/compose110")
    val compileClasspathFiles = compose110Classpath.incoming.files

    inputs.dir(sourceDir)
    inputs.files(compileClasspathFiles)
    outputs.dir(outputDir)

    classpath = compose110KotlinCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")

    argumentProviders.add(
      CommandLineArgumentProvider {
        val cp = compileClasspathFiles.files.joinToString(File.pathSeparator)
        outputDir.get().asFile.mkdirs()
        listOf(
          sourceDir.absolutePath,
          "-classpath",
          cp,
          "-d",
          outputDir.get().asFile.absolutePath,
          "-jvm-target",
          "1.8",
          "-language-version",
          "1.9",
          "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
          "-Xsuppress-version-warnings",
          "-no-stdlib",
        )
      }
    )
  }

// Make compose110 output available to the Android Kotlin compilation
val compose110Output = files(compileCompose110.map { it.outputs.files })

tasks.withType<KotlinCompile>().configureEach {
  if (name == "compileReleaseKotlinAndroid" || name == "compileDebugKotlinAndroid") {
    dependsOn(compileCompose110)
    libraries.from(compose110Output)
  }
}

// Include compose110 classes in the AAR
android.libraryVariants.all {
  registerPreJavacGeneratedBytecode(project.files(compileCompose110.map { it.outputs.files }))
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<DokkaTask>().configureEach {
  // suppress unattached source sets for docs
  dokkaSourceSets {
    matching {
        it.name.contains("androidandroid", ignoreCase = true) ||
          it.name.contains("testfixtures", ignoreCase = true)
      }
      .configureEach { suppress.set(true) }
  }
}
