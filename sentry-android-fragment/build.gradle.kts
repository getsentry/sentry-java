import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.detekt)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.android.fragment"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    // for AGP 4.1
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
  }

  buildTypes {
    getByName("debug") { consumerProguardFiles("proguard-rules.pro") }
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
  }

  // AGP 9 only generates unit tests for the testBuildType. CI disables the debug
  // variant, so unit tests must target release to run at all.
  testBuildType = "release"

  kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_1_8
    compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_9
    compilerOptions.apiVersion = KotlinVersion.KOTLIN_1_9
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

  compileOnly(libs.androidx.fragment.ktx)

  // tests
  testImplementation(libs.androidx.fragment.ktx)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}
