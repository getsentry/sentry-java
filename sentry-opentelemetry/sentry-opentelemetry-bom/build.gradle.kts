plugins {
  `java-platform`
  `maven-publish`
}

javaPlatform.allowDependencies()

dependencies {
  api(platform(libs.otel.bom))
  api(platform(libs.otel.alpha.bom))
  api(platform(libs.otel.instrumentation.bom))
  api(platform(libs.otel.instrumentation.alpha.bom))

  constraints {
    api(projects.sentryOpentelemetry.sentryOpentelemetryAgent)
    api(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    api(projects.sentryOpentelemetry.sentryOpentelemetryAgentless)
    api(projects.sentryOpentelemetry.sentryOpentelemetryAgentlessSpring)
    api(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
    api(projects.sentryOpentelemetry.sentryOpentelemetryCore)
    api(projects.sentryOpentelemetry.sentryOpentelemetryOtlp)
    api(projects.sentryOpentelemetry.sentryOpentelemetryOtlpSpring)
  }
}
