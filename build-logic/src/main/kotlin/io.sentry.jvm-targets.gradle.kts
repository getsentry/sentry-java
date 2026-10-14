import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Compilation and tests run on this JDK whatever JDK runs Gradle, so a contributor on a newer JDK
// gets the same output as CI. Error Prone 2.50 ships Java 21 bytecode and needs javac 21 as well.
// This picks the JDK only - every module pins the bytecode level it publishes itself.
val buildJdk = JavaLanguageVersion.of(21)

plugins.withType<JavaBasePlugin> {
  extensions.configure<JavaPluginExtension> { toolchain { languageVersion.set(buildJdk) } }
}

/**
 * Targets [buildJdk] in the compile tasks that [taskFilter] selects. Both languages move together,
 * because the Kotlin plugin rejects a Java/Kotlin target mismatch.
 */
fun Project.applyToolchainToCompileTasks(taskFilter: (Task) -> Boolean) {
  // AGP assigns these task properties while it creates the variants, so this has to run later.
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

listOf("com.android.library", "com.android.application").forEach { pluginId ->
  plugins.withId(pluginId) {
    // Mockito 5 and mockito-kotlin 6 are built for Java 11, so test code (never published) can't
    // share the Java 8 target of the published code. Other modules pin main compilation only, which
    // leaves their test tasks on the toolchain; AGP has no such split, as compileOptions applies to
    // every variant, so its unit tests need this.
    applyToolchainToCompileTasks { it.name.contains("UnitTest") }
  }
}

// Published code targets Java 8. release compiles against that version's class library instead of
// the build JDK's, so newer JDK APIs (e.g. List.getFirst()) can't leak into published bytecode.
// Modules that publish a higher level override this; test code and samples are never published and
// follow the toolchain.
// Keep this in sync with the publication filter in the root build script.
val isPublished =
  !name.contains("sample") &&
    !name.contains("integration-tests") &&
    name != "sentry-system-test-support" &&
    name != "sentry-test-support"

if (isPublished) {
  plugins.withId("java-library") {
    tasks.named<JavaCompile>("compileJava") { options.release.set(8) }
  }
}
