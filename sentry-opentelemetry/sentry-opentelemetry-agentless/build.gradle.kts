import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.buildconfig)
  id("io.sentry.animalsniffer")
}

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
}

dependencies {
  api(projects.sentry)
  implementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
  implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
  api(libs.otel)
  api(libs.otel.semconv)
  api(libs.otel.semconv.incubating)
  api(libs.otel.extension.autoconfigure)

  testImplementation(projects.sentryAsyncProfiler)
  testImplementation(projects.sentryTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.google.truth)
}

// Sentry's OpenTelemetry customizer only initializes Sentry when the environment asks it to.
tasks.test { environment("SENTRY_AUTO_INIT", "true") }

buildConfig {
  useJavaOutput()
  packageName("io.sentry.opentelemetry.agentless")
  buildConfigField(
    "String",
    "SENTRY_OPENTELEMETRY_AGENTLESS_SDK_NAME",
    "\"${Config.Sentry.SENTRY_OPENTELEMETRY_AGENTLESS_SDK_NAME}\"",
  )
  buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.jar {
  manifest {
    attributes(
      "Sentry-Opentelemetry-SDK-Name" to Config.Sentry.SENTRY_OPENTELEMETRY_AGENTLESS_SDK_NAME,
      "Sentry-Version-Name" to project.version,
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_OPENTELEMETRY_AGENTLESS_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-opentelemetry-agentless",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
