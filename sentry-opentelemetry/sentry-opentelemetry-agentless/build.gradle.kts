plugins {
    `java-library`
}

dependencies {
    api(projects.sentry)
    implementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
    implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    api(Config.Libs.OpenTelemetry.otelSdk)
    api(Config.Libs.OpenTelemetry.otelSemconv)
    api(Config.Libs.OpenTelemetry.otelSemconvIncubating)
    api(Config.Libs.OpenTelemetry.otelExtensionAutoconfigure)
}
