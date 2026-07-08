plugins {
  `java-library`
  alias(libs.plugins.animalsniffer)
  id("io.sentry.javadoc")
}

dependencies {
  api(projects.sentryOpentelemetry.sentryOpentelemetryOtlp)
  implementation(libs.springboot3.otel)
  signature("org.codehaus.mojo.signature:java18:${libs.versions.java18Signature.get()}@signature")
}

tasks { check { dependsOn(animalsnifferMain) } }

tasks.jar {
  manifest {
    attributes(
      "Sentry-Version-Name" to project.version,
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_OPENTELEMETRY_OTLP_SPRING_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-opentelemetry-otlp-spring",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
