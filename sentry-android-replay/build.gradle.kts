import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.gradle.versions)
  id("io.sentry.detekt")
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
  id("io.sentry.publish")
  id("io.sentry.api-validation")
  id("io.sentry.robolectric")
}

// Session Replay calls Compose internals, whose mangled JVM names change between Compose versions.
// Creating extra build types to run tests against the latest stable, and latest preview version.
// These are heavy Robolectric runs, so they stay out of `check` and only exist when the
// composeUiTestMatrix property is set. CI runs them in their own job:
// ./gradlew :sentry-android-replay:testComposeUiMatrix -PcomposeUiTestMatrix
val composeUiTestVersions =
  if (providers.gradleProperty("composeUiTestMatrix").isPresent) {
    mapOf("composeUi112" to "1.12.1", "composeUi113" to "1.13.0-rc01")
  } else {
    emptyMap()
  }

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  compileSdkMinor = libs.versions.compileSdkMinor.get().toInt()
  namespace = "io.sentry.android.replay"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // for AGP 4.1
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
  }

  buildFeatures { compose = true }

  composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }

  buildTypes {
    getByName("debug") { consumerProguardFiles("proguard-rules.pro") }
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
    composeUiTestVersions.keys.forEach { name ->
      create(name) {
        initWith(getByName("release"))
        matchingFallbacks += "release"
      }
    }
  }

  // AGP 9 only generates unit tests for the testBuildType. The debug variant is
  // disabled, so unit tests must target release to run at all.
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

    // Lint 9.5 can't load the custom checks bundled with older androidx.lifecycle releases and
    // reports that it skips them. They only cover lifecycle APIs this module doesn't call.
    disable += "ObsoleteLintCustomCheck"
  }

  buildFeatures { buildConfig = true }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    if (it.buildType in composeUiTestVersions) {
      (it as HasHostTestsBuilder).hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
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
  testImplementation(libs.google.truth)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.core)
  testImplementation(libs.androidx.compose.ui)
  testImplementation(libs.androidx.compose.foundation)
  testImplementation(libs.androidx.compose.foundation.layout)
  testImplementation(libs.androidx.compose.material3)
  testImplementation(libs.coil.compose)
}

composeUiTestVersions.forEach { (buildType, version) ->
  configurations
    .matching { it.name == "${buildType}UnitTestRuntimeClasspath" }
    .configureEach {
      resolutionStrategy.eachDependency {
        if (requested.group == "androidx.compose.ui") useVersion(version)
      }
    }
}

if (composeUiTestVersions.isNotEmpty()) {
  tasks.register("testComposeUiMatrix") {
    group = "verification"
    description = "Runs the unit tests against each Compose UI version in composeUiTestVersions."
    dependsOn(
      composeUiTestVersions.keys.map { "test${it.replaceFirstChar(Char::uppercase)}UnitTest" },
    )
  }
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
  compilerOptions.freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
}
