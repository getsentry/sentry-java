plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  id("com.apollographql.apollo") version "4.1.1"
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
}

dependencies {
  api(projects.sentry)
  api(projects.sentryTestSupport)
  api(libs.apollo4.kotlin)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)

  compileOnly(libs.springboot.starter.web)

  implementation(libs.jackson.databind)
  implementation(libs.jackson.kotlin)
  implementation(libs.okhttp)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)

  // tests
  implementation(kotlin(Config.kotlinStdLib))
  implementation(libs.kotlin.test.junit)
  implementation(libs.mockito.kotlin)
}

apollo {
  service("service") {
    srcDir("src/main/graphql")
    packageName.set("io.sentry.samples.graphql")
    outputDirConnection { connectToKotlinSourceSet("main") }
  }
}
