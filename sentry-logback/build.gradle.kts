import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.animalsniffer)
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
}

dependencies {
  api(projects.sentry)
  compileOnly(libs.logback.classic)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  signature("org.codehaus.mojo.signature:java18:${libs.versions.java18Signature.get()}@signature")

  // tests
  testImplementation(projects.sentryTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.logback.classic)
  testImplementation(libs.mockito.kotlin)
}

tasks { check { dependsOn(animalsnifferMain) } }

buildConfig {
  useJavaOutput()
  packageName("io.sentry.logback")
  buildConfigField(
    "String",
    "SENTRY_LOGBACK_SDK_NAME",
    "\"${Config.Sentry.SENTRY_LOGBACK_SDK_NAME}\"",
  )
  buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.withType<JavaCompile>().configureEach {
  dependsOn(tasks.generateBuildConfig)
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.sentry")
  }
}

tasks.jar {
  manifest {
    attributes(
      "Sentry-Version-Name" to project.version,
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_LOGBACK_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-logback",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
