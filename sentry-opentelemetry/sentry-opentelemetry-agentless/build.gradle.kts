plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.animalsniffer)
}

dependencies {
  api(projects.sentry)
  implementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
  implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
  api(libs.otel)
  api(libs.otel.semconv)
  api(libs.otel.semconv.incubating)
  api(libs.otel.extension.autoconfigure)
  signature("org.codehaus.mojo.signature:java18:${libs.versions.java18Signature.get()}@signature")
}

tasks { check { dependsOn(animalsnifferMain) } }

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
