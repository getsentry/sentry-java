import io.gitlab.arturbosch.detekt.Detekt
import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.detekt)
}

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.uitest.android.benchmark"

  defaultConfig {
    applicationId = "io.sentry.uitest.android.benchmark"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Runs each test in its own instance of Instrumentation. This way they are isolated from
    // one another and get their own Application instance.
    // https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner#enable-gradle
    // This doesn't work on some devices with Android 11+. Clearing package data resets permissions.
    // Check the readme for more info.
    testInstrumentationRunnerArguments["clearPackageData"] = "true"
  }

  testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }

  buildFeatures {
    // Determines whether to support View Binding.
    // Note that the viewBinding.enabled property is now deprecated.
    viewBinding = true
  }

  signingConfigs {
    getByName("debug") {
      storeFile = rootProject.file("debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  testBuildType = System.getProperty("testBuildType", "debug")

  buildTypes {
    getByName("debug") {
      isMinifyEnabled = true
      signingConfig = signingConfigs.getByName("debug")
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "benchmark-proguard-rules.pro",
      )
      testProguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "benchmark-proguard-rules.pro",
      )
    }
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "benchmark-proguard-rules.pro",
      )
      testProguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "benchmark-proguard-rules.pro",
      )
    }
  }

  kotlin { compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8 }

  lint {
    warningsAsErrors = true
    checkDependencies = true

    // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
    checkReleaseBuilds = false
  }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

dependencies {
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentryAndroid)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.core)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.test.espresso.idling.resource)

  compileOnly(libs.nopen.annotations)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  androidTestUtil(libs.androidx.test.orchestrator)
  androidTestImplementation(projects.sentryTestSupport)
  androidTestImplementation(libs.kotlin.test.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.test.core.ktx)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
}

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.sentry")
    option("NullAway:UnannotatedSubPackages", "io.sentry.uitest.android.benchmark.databinding")
  }
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}
