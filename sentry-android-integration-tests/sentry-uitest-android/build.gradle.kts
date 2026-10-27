import io.gitlab.arturbosch.detekt.Detekt
import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion

plugins {
  id("com.android.application")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.detekt)
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

val sentryVersion = libs.versions.sentry.get()

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()
  namespace = "io.sentry.uitest.android"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.compileSdk.get().toInt()
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // Runs each test in its own instance of Instrumentation. This way they are isolated from
    // one another and get their own Application instance.
    // https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner#enable-gradle
    // This doesn't work on some devices with Android 11+. Clearing package data resets permissions.
    // Check the readme for more info.
    testInstrumentationRunnerArguments["clearPackageData"] = "true"
    buildConfigField("String", "ENVIRONMENT", "\"${System.getProperty("environment", "")}\"")
  }

  testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" }

  buildFeatures {
    // Determines whether to support View Binding.
    // Note that the viewBinding.enabled property is now deprecated.
    viewBinding = true
    compose = true
    buildConfig = true
  }

  composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }

  signingConfigs {
    getByName("debug") {
      storeFile = file("../../debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  testBuildType = "release"

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
      testProguardFiles("proguard-rules.pro")
    }
  }

  lint {
    warningsAsErrors = true
    checkDependencies = true

    // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
    checkReleaseBuilds = false
  }

  androidComponents.beforeVariants {
    if (it.buildType == "debug") {
      it.enable = false
    }
  }
}

val applySentryIntegrations = System.getenv("APPLY_SENTRY_INTEGRATIONS")?.toBoolean() ?: true

if (applySentryIntegrations) {
  android.sourceSets["androidTest"].java.srcDirs("src/androidTestReplay/java")
}

dependencies {
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))

  if (applySentryIntegrations) {
    implementation("io.sentry:sentry-android:$sentryVersion")
    implementation("io.sentry:sentry-compose:$sentryVersion")
  } else {
    implementation("io.sentry:sentry-android-core:$sentryVersion")
  }
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.foundation)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.core)
  implementation(libs.androidx.recyclerview)
  implementation(libs.androidx.test.espresso.idling.resource)
  implementation(libs.leakcanary)

  compileOnly(libs.nopen.annotations)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  androidTestUtil(libs.androidx.test.orchestrator)
  androidTestImplementation("io.sentry:sentry-test-support:$sentryVersion")
  androidTestImplementation(libs.androidx.test.core.ktx)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.rules)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.awaitility3.kotlin)
  androidTestImplementation(libs.kotlin.test.junit)
  androidTestImplementation(libs.leakcanary.instrumentation)
  androidTestImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.sentry")
    option("NullAway:UnannotatedSubPackages", "io.sentry.uitest.android.databinding")
  }
}

detekt {
  buildUponDefaultConfig = true
  allRules = true
  config.setFrom(file("../../detekt.yml"))
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}
