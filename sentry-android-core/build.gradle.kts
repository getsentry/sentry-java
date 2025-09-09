import net.ltgt.gradle.errorprone.errorprone

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  jacoco
  alias(libs.plugins.jacoco.android)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.android.core"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField(
      "String",
      "SENTRY_ANDROID_SDK_NAME",
      "\"${Config.Sentry.SENTRY_ANDROID_SDK_NAME}\"",
    )

    // for AGP 4.1
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
  }

  buildTypes {
    getByName("debug") { consumerProguardFiles("proguard-rules.pro") }
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
  }

  kotlin { compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8 }

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

  // needed because of Kotlin 1.4.x
  configurations.all { resolutionStrategy.force(libs.jetbrains.annotations.get()) }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.sentry")
  }
}

dependencies {
  api(projects.sentry)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(projects.sentryAndroidFragment)
  compileOnly(projects.sentryAndroidTimber)
  compileOnly(projects.sentryAndroidReplay)
  compileOnly(projects.sentryCompose)

  // lifecycle processor, session tracking
  implementation(libs.androidx.lifecycle.common.java8)
  implementation(libs.androidx.lifecycle.process)
  implementation(libs.androidx.core)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(kotlin(Config.kotlinStdLib, Config.kotlinStdLibVersionAndroid))
  testImplementation(libs.roboelectric)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.androidx.core.ktx)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.test.runner)
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
  testImplementation(projects.sentryTestSupport)
  testImplementation(projects.sentryAndroidFragment)
  testImplementation(projects.sentryAndroidTimber)
  testImplementation(projects.sentryAndroidReplay)
  testImplementation(projects.sentryCompose)
  testImplementation(projects.sentryAndroidNdk)
  testRuntimeOnly(libs.androidx.compose.ui)
  testRuntimeOnly(libs.androidx.fragment.ktx)
  testRuntimeOnly(libs.timber)
}
