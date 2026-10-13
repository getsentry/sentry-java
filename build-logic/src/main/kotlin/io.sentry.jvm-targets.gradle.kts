import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/**
 * Picks the JDK that compiles the code and runs the tests, and lifts test code off the Java 8
 * target that the published modules use.
 */
val buildJdk = JavaLanguageVersion.of(21)

// Compile and run tests on this JDK whatever JDK runs Gradle. Error Prone 2.50 ships Java 21
// bytecode, so javac itself has to be 21; without a toolchain, building on JDK 17 fails with
// "UnsupportedClassVersionError: com/google/errorprone/ErrorProneJavacPlugin".
// This selects the JDK only; each module pins the bytecode level of its published code itself.
plugins.withType<JavaBasePlugin> {
  extensions.configure<JavaPluginExtension> { toolchain { languageVersion.set(buildJdk) } }
}

// Mockito 5 and mockito-kotlin 6 are built for Java 11, so test code (never published) can't share
// the Java 8 target of the published code. The library modules pin main compilation only, so their
// test tasks follow the toolchain on their own. Two groups can't do that and need the override
// below: Android modules, because AGP applies compileOptions to every variant including the unit
// tests, and the samples, which still pin every compile task and consume sentry-system-test-support.
fun Project.followToolchainInTests(taskFilter: (Task) -> Boolean) {
  // Both languages have to move together or the Kotlin plugin rejects the mismatch. AGP sets these
  // task properties while creating the variants, so the override has to run after evaluation.
  afterEvaluate {
    tasks.withType<JavaCompile>().matching(taskFilter).configureEach {
      sourceCompatibility = buildJdk.toString()
      targetCompatibility = buildJdk.toString()
    }
    tasks.withType<KotlinJvmCompile>().matching(taskFilter).configureEach {
      compilerOptions.jvmTarget.set(JvmTarget.fromTarget(buildJdk.toString()))
    }
  }
}

if (path.startsWith(":sentry-samples")) {
  plugins.withId("java") { followToolchainInTests { it.name.contains("Test") } }
}

listOf("com.android.library", "com.android.application").forEach { pluginId ->
  plugins.withId(pluginId) {
    followToolchainInTests { it.name.contains("UnitTest") }

    // Robolectric's SDK 37 sandbox (the default, as targetSdk is 37) reflectively writes
    // FileDescriptor fields in ApplicationSharedMemory; without this, setup fails with
    // "Failed to interact with raw FileDescriptor internals".
    tasks.withType<Test>().configureEach { jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED") }
  }
}
