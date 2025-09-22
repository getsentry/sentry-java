import io.gitlab.arturbosch.detekt.Detekt

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  jacoco
  alias(libs.plugins.jacoco.android)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.detekt)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.android.timber"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // for AGP 4.1
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    buildConfigField(
      "String",
      "SENTRY_TIMBER_SDK_NAME",
      "\"${Config.Sentry.SENTRY_TIMBER_SDK_NAME}\"",
    )
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

  compileOnly(libs.timber)

  implementation(kotlin(Config.kotlinStdLib, Config.kotlinStdLibVersionAndroid))

  // tests
  testImplementation(libs.timber)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}
