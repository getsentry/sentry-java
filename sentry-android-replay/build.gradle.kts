import com.android.build.api.variant.HasTestSuites
import com.android.build.api.variant.TestSuiteSourceSet
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
  id("com.android.library")
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
val composeUiTestVersions = mapOf("composeUi112" to "1.12.1", "composeUi113" to "1.13.0-rc01")

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

  // SPIKE: built-in Kotlin requires test and main code to share one JVM target.
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  composeOptions { kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get() }

  buildTypes {
    getByName("debug") { consumerProguardFiles("proguard-rules.pro") }
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
  }

  // AGP 9 only generates unit tests for the testBuildType. The debug variant is
  // disabled, so unit tests must target release to run at all.
  testBuildType = "release"

  testOptions {
    animationsDisabled = true
    unitTests.apply {
      isReturnDefaultValues = true
      isIncludeAndroidResources = true
    }
    composeUiTestVersions.keys.forEach { name ->
      customSuites.create(name) {
        targetVariants += "release"
        targets.create("jvm")
        hostJar { enableAndroidResources = true }
        useJunitEngine {
          includeEngines += "junit-vintage"
          enginesDependencies.add("org.junit.vintage:junit-vintage-engine:5.12.2")
          enginesDependencies.add("org.junit.platform:junit-platform-launcher:1.12.2")
        }
      }
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
  }
  androidComponents.onVariants { variant ->
    (variant as HasTestSuites).suites.values.forEach { suite ->
      suite.sources.filterIsInstance<TestSuiteSourceSet.HostJar>().forEach {
        it.kotlin?.addStaticSourceDirectory("src/test/java")
        it.java?.addStaticSourceDirectory("src/test/java")
        it.resources.addStaticSourceDirectory("src/test/resources")
      }
    }
  }
}

kotlin {
  explicitApi()
  compilerOptions.jvmTarget = JvmTarget.JVM_21 // SPIKE
  compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = KotlinVersion.KOTLIN_1_9
}

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

composeUiTestVersions.forEach { (suite, version) ->
  val variantSuite = "release${suite.replaceFirstChar { it.uppercase() }}"
  configurations
    .matching { it.name == "${variantSuite}Implementation" }
    .configureEach { extendsFrom(configurations.testImplementation.get()) }
  configurations
    .matching {
      it.name == "${variantSuite}RuntimeClasspath" ||
        it.name == "${variantSuite}HostRuntimeClasspath"
    }
    .configureEach {
      resolutionStrategy.eachDependency {
        if (requested.group == "androidx.compose.ui") useVersion(version)
      }
    }
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
  compilerOptions.freeCompilerArgs.add("-opt-in=androidx.compose.ui.ExperimentalComposeUiApi")
}

// SPIKE: suites don't get the mockable android.jar that AGP puts on unit test classpaths.
afterEvaluate {
  tasks
    .withType<Test>()
    .matching { it.name.endsWith("JvmTestSuite") }
    .configureEach {
      classpath +=
        tasks.named<Test>("testReleaseUnitTest").get().classpath.filter { it.name == "android.jar" }
      // SPIKE: nor the suite's own processed Java resources.
      classpath +=
        files(tasks.named(name.replace("test", "process").replace("JvmTestSuite", "JavaRes")))
    }
}

// SPIKE: AGP resolves suite runtime classpaths for the standard JVM, which picks Compose's desktop
// artifacts instead of the Android ones.
afterEvaluate {
  configurations
    .matching { it.name.endsWith("HostRuntimeClasspath") }
    .configureEach {
      attributes {
        attribute(
          TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
          objects.named(TargetJvmEnvironment.ANDROID),
        )
        attribute(
          org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.attribute,
          org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.androidJvm,
        )
      }
    }
}

// SPIKE: suites don't get the test_config.properties Robolectric reads its resources from.
composeUiTestVersions.keys.forEach { suite ->
  val v = "release${suite.replaceFirstChar { it.uppercase() }}"
  val writeConfig =
    tasks.register("write${v}TestConfig") {
      val out = layout.buildDirectory.dir("spike/$v")
      outputs.dir(out)
      doLast {
        val f = out.get().file("com/android/tools/test_config.properties").asFile
        f.parentFile.mkdirs()
        f.writeText(
          """
        android_custom_package=io.sentry.android.replay
        android_merged_assets=build/intermediates/assets/$v/merge${v.replaceFirstChar { it.uppercase() }}Assets
        android_merged_manifest=build/intermediates/packaged_manifests/$v/process${v.replaceFirstChar { it.uppercase() }}Manifest/AndroidManifest.xml
        android_resource_apk=build/intermediates/apk_for_local_test/$v/package${v.replaceFirstChar { it.uppercase() }}ForUnitTest/apk-for-local-test.ap_
        """
            .trimIndent(),
        )
      }
    }
  afterEvaluate {
    tasks.named<Test>("test${v.replaceFirstChar { it.uppercase() }}JvmTestSuite") {
      classpath += files(writeConfig)
      dependsOn(
        "package${v.replaceFirstChar { it.uppercase() }}ForUnitTest",
        "merge${v.replaceFirstChar { it.uppercase() }}Assets",
        "process${v.replaceFirstChar { it.uppercase() }}Manifest",
      )
    }
  }
}

// SPIKE: AGP doesn't hook test suites into check.
afterEvaluate {
  tasks.named("check") { dependsOn(tasks.matching { it.name.endsWith("JvmTestSuite") }) }
}
