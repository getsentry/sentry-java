plugins {
    `java-library`
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
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
