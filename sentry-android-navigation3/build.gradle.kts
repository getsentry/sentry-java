import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.kotlin.serialization)
  id("io.sentry.detekt")
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
  id("io.sentry.publish")
  id("io.sentry.api-validation")
  id("io.sentry.robolectric")
}

val kotlinxSerializationFloor = matrixLibs.versions.kotlinxSerialization.floor.get()

android {
  compileSdk = libs.versions.compileSdk.get().toInt()
  namespace = "io.sentry.compose.navigation3"

  defaultConfig {
    minSdk = libs.versions.minSdk.get().toInt()

    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
  }

  buildTypes {
    getByName("release") { consumerProguardFiles("proguard-rules.pro") }
  }

  // AGP 9 only generates unit tests for the testBuildType. The debug variant is
  // disabled, so unit tests must target release to run at all.
  testBuildType = "release"

  kotlin {
    compilerOptions.jvmTarget = JVM_1_8
    compilerOptions.languageVersion = KotlinVersion.KOTLIN_1_9
    compilerOptions.apiVersion = KotlinVersion.KOTLIN_1_9
  }

  testOptions {
    unitTests.apply {
      isReturnDefaultValues = true
      // AGP 9.5 can't create the Compose Preview task without it, which breaks any
      // build that creates every task, such as an LSP's Gradle import.
      isIncludeAndroidResources = true
      // Lets tests check that MIN_KOTLINX_SERIALIZATION_VERSION matches the catalog's floor.
      all { it.systemProperty("nav3.kotlinxSerializationFloor", kotlinxSerializationFloor) }
    }
  }

  lint {
    warningsAsErrors = true
    checkDependencies = true

    // Lifecycle 2.8.3's lint checks are incompatible with AGP 9.5. Lifecycle 2.10.0 fixes them,
    // but raises minSdk to 23 while this SDK still supports 21.
    disable += "ObsoleteLintCustomCheck"

    // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
    checkReleaseBuilds = false
  }

  buildFeatures {
    buildConfig = true
    compose = true
  }

  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

kotlin { explicitApi() }

// Unit tests run against the kotlinx.serialization floor by default. To run them against a newer
// version, as apps may ship a newer kotlinx.serialization dependency than
// sentry-android-navigation3 compiles against, pass -Pnav3TestKotlinxVersion=<version>.
val testKotlinxVersion =
  providers.gradleProperty("nav3TestKotlinxVersion").orNull ?: kotlinxSerializationFloor

dependencies {
  implementation(projects.sentry)
  compileOnly(libs.androidx.lifecycle.runtime.compose)

  compileOnly(libs.androidx.compose.runtime)
  compileOnly(libs.androidx.compose.ui)
  // Every Nav3 release depends on kotlinx.serialization (navigation3-runtime declares the floor,
  // 1.7.3, directly), so this never raises a Nav3 app's version. It guarantees the built-in
  // mappers' classes are present for apps that use this module without Nav3. A minimum, as the
  // version is
  // published: strictly() would force it on apps.
  implementation(matrixLibs.kotlinxSerialization.core) {
    version { require(kotlinxSerializationFloor) }
  }
  // Compiles against exactly the floor. Strict, as other compile dependencies (e.g., Compose UI
  // via savedstate) also bring in kotlinx.serialization. With a mere minimum, a bump of one of
  // those could silently raise the version we compile against, and the same source could then
  // compile to calls that the floor version's runtime lacks. compileOnly, so it isn't published.
  // verifyKotlinxSerializationCompileVersion checks the result.
  compileOnly(matrixLibs.kotlinxSerialization.core) {
    version { strictly(kotlinxSerializationFloor) }
  }

  testImplementation(projects.sentryAndroidCore)
  testImplementation(projects.sentryTestSupport)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.fragment.ktx)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.test.ext.junit)
  testImplementation(libs.androidx.viewpager2)
  testImplementation(libs.google.truth)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(matrixLibs.kotlinxSerialization.core) {
    // A minimum, not strictly(), so that a test dependency that needs a newer version (e.g., a
    // future Nav3) raises the resolved version instead of being silently downgraded, and
    // verifyKotlinxSerializationTestVersion fails.
    version { require(testKotlinxVersion) }
  }
  testImplementation(libs.mockito.core)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.roboelectric)
}

/**
 * Registers a task that fails the build if [configurationName] resolves kotlinx-serialization-core
 * to any version other than [expectedVersion], naming the dependencies that requested another one.
 * [problem] describes why the version matters, and [fix] what to do if it's wrong.
 */
fun registerKotlinxSerializationVersionCheck(
  name: String,
  configurationName: String,
  expectedVersion: String,
  problem: String,
  fix: String,
) =
  tasks.register(name) {
    val resolution =
      configurations.named(configurationName).flatMap { classpath ->
        classpath.incoming.resolutionResult.rootComponent.map { root ->
          val resolved = sortedSetOf<String>()
          val requests = sortedSetOf<String>()
          val visited = mutableSetOf<ResolvedComponentResult>()
          fun visit(component: ResolvedComponentResult) {
            if (!visited.add(component)) return
            val id = component.id
            if (
              id is ModuleComponentIdentifier &&
                id.group == "org.jetbrains.kotlinx" &&
                id.module.startsWith("kotlinx-serialization-core")
            ) {
              resolved += "${id.module}:${id.version}"
            }
            for (dependency in component.dependencies) {
              val requested = dependency.requested.displayName
              if (
                "org.jetbrains.kotlinx:kotlinx-serialization-core" in requested &&
                  !dependency.isConstraint &&
                  !requested.endsWith(":$expectedVersion") &&
                  // kotlinx-serialization-core requests its own -jvm variant.
                  !id.displayName.startsWith("org.jetbrains.kotlinx:kotlinx-serialization-core")
              ) {
                requests += "${dependency.from.id.displayName} requests $requested"
              }
              when (dependency) {
                is ResolvedDependencyResult -> visit(dependency.selected)
                is UnresolvedDependencyResult ->
                  if ("kotlinx-serialization-core" in requested) {
                    resolved += "$requested (unresolved)"
                  }
              }
            }
          }
          visit(root)
          listOf(resolved.toList(), requests.toList())
        }
      }
    inputs.property("expectedVersion", expectedVersion)
    inputs.property("resolution", resolution)
    doLast {
      val (resolved, requests) = resolution.get()
      val unexpected = resolved.filterNot { it.endsWith(":$expectedVersion") }
      check(resolved.isNotEmpty() && unexpected.isEmpty()) {
        "$problem, but $configurationName resolved " +
          "${resolved.ifEmpty { listOf("no kotlinx-serialization-core") }}" +
          (if (requests.isEmpty()) "" else " (${requests.joinToString("; ")})") +
          ". $fix"
      }
    }
  }

/**
 * Fails the build if the release compile classpath resolves kotlinx.serialization to any version
 * other than the floor, e.g., because another dependency declared a conflicting strict version or a
 * resolution rule forced one. We must compile against the floor, as that's the oldest runtime the
 * module promises to work with.
 */
val verifyKotlinxSerializationCompileVersion =
  registerKotlinxSerializationVersionCheck(
    name = "verifyKotlinxSerializationCompileVersion",
    configurationName = "releaseCompileClasspath",
    expectedVersion = kotlinxSerializationFloor,
    problem =
      "sentry-android-navigation3 must compile against kotlinx.serialization " +
        "$kotlinxSerializationFloor (the kotlinxSerialization-floor in " +
        "gradle/dependency-matrix.versions.toml)",
    fix = "Find what changed the version, e.g., a strict version or a resolution rule.",
  )

/**
 * Fails the build if the unit tests would run against a kotlinx.serialization version other than
 * the one under test, e.g., because a test dependency such as Nav3 now requires a newer one. The
 * tests would then not test that version, and a pin that downgraded the dependency instead would
 * run it on a version it doesn't support, which no real app can have.
 */
val verifyKotlinxSerializationTestVersion =
  registerKotlinxSerializationVersionCheck(
    name = "verifyKotlinxSerializationTestVersion",
    configurationName = "releaseUnitTestRuntimeClasspath",
    expectedVersion = testKotlinxVersion,
    problem = "The unit tests must run against kotlinx.serialization $testKotlinxVersion",
    fix =
      "If a test dependency (e.g., navigation3-runtime) now requires a newer version, test the " +
        "floor with the newest release of that dependency that accepts the floor, as apps on " +
        "older releases still run the floor. Raise kotlinxSerialization-floor only to drop " +
        "support for those apps. See \"Version floor\" in the kotlinx package README.",
  )

tasks
  .named { it == "compileReleaseKotlin" }
  .configureEach {
    dependsOn(verifyKotlinxSerializationCompileVersion)
  }

tasks
  .named { it == "testReleaseUnitTest" }
  .configureEach {
    dependsOn(verifyKotlinxSerializationTestVersion)
  }

tasks.named("check") {
  dependsOn(verifyKotlinxSerializationCompileVersion, verifyKotlinxSerializationTestVersion)
}

tasks.withType<Detekt>().configureEach {
  // Target version of the generated JVM bytecode. It is used for type resolution.
  jvmTarget = JavaVersion.VERSION_1_8.toString()
}
