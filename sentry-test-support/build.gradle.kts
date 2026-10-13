plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
}

// Not published and test-only, so it keeps the toolchain's JVM target instead of pinning Java 8
// like the published modules do.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
}

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
}
