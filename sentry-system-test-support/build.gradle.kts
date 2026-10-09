plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  id("com.apollographql.apollo") version "4.1.1"
}

// Not published. Mockito 5 and mockito-kotlin 5+ are built for Java 11.
configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_11
  targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
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
