plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  jacoco
  alias(libs.plugins.jacoco.android)
  alias(libs.plugins.gradle.versions)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.launchdarkly.android"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // for AGP 4.1
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    buildConfigField(
      "String",
      "SENTRY_LAUNCHDARKLY_ANDROID_SDK_NAME",
      "\"${Config.Sentry.SENTRY_LAUNCHDARKLY_ANDROID_SDK_NAME}\"",
    )
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

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

dependencies {
  api(projects.sentry)

  compileOnly(libs.launchdarkly.android)
  compileOnly(libs.jetbrains.annotations)

  // tests
  testImplementation(projects.sentry)
  testImplementation(projects.sentryTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib, Config.kotlinStdLibVersionAndroid))
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
  testImplementation(libs.launchdarkly.android)
}
