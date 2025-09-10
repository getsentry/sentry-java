import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  jacoco
  alias(libs.plugins.jacoco.android)
  alias(libs.plugins.gradle.versions)
  // TODO: enable it later
  //    alias(libs.plugins.detekt)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.android.replay"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // for AGP 4.1
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
  }

  buildFeatures { compose = true }

  composeOptions {
    kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    useLiveLiterals = false
  }

  buildTypes {
    getByName("debug") { consumerProguardFiles("proguard-rules.pro") }
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
  }

  kotlin {
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
    compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
    compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
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

kotlin { explicitApi() }

dependencies {
  api(projects.sentry)

  compileOnly(libs.androidx.compose.ui.replay)
  implementation(kotlin(Config.kotlinStdLib, Config.kotlinStdLibVersionAndroid))
  // tests
  testImplementation(projects.sentryTestSupport)
  testImplementation(projects.sentryAndroidCore)
  testImplementation(libs.roboelectric)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.androidx.activity.compose)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
  testImplementation(libs.androidx.compose.ui)
  testImplementation(libs.androidx.compose.foundation)
  testImplementation(libs.androidx.compose.foundation.layout)
  testImplementation(libs.androidx.compose.material3)
  testImplementation(libs.coil.compose)
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
  compilerOptions.freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
}
