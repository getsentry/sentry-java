plugins {
    java
    application
    id(Config.QualityPlugins.gradleVersions)
}

application {
    mainClass.set("io.sentry.samples.console.Main")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(projects.sentry)

    implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    implementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
    implementation(Config.Libs.OpenTelemetry.otelSdk)
    implementation(Config.Libs.OpenTelemetry.otelExtensionAutoconfigure)
    implementation(Config.Libs.OpenTelemetry.otelSemconv)
    implementation(Config.Libs.OpenTelemetry.otelSemconvIncubating)
}
