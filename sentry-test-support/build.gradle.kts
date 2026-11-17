plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

// Android unit tests compile for the SDK's Java 11 target, and Kotlin can't inline bytecode of a
// higher target into theirs.
tasks.compileKotlin { compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11 }

tasks.compileJava { options.release = 11 }

dependencies {
  api(projects.sentry)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)

  // tests
  implementation(kotlin(Config.kotlinStdLib))
  implementation(libs.kotlin.test.junit)
  implementation(libs.mockito.kotlin)

  testImplementation(libs.google.truth)
}
