plugins {
    `java-library`
    id(Config.BuildPlugins.buildConfig) version Config.BuildPlugins.buildConfigVersion
}

dependencies {
    api(projects.sentry)
    implementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
    implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    api(Config.Libs.OpenTelemetry.otelSdk)
    api(Config.Libs.OpenTelemetry.otelSemconv)
    api(Config.Libs.OpenTelemetry.otelSemconvIncubating)
    api(Config.Libs.OpenTelemetry.otelExtensionAutoconfigure)
    api(Config.Libs.springBoot3StarterOpenTelemetry)
}

buildConfig {
    useJavaOutput()
    packageName("io.sentry.opentelemetry.agentless.spring")
    buildConfigField("String", "SENTRY_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME", "\"${Config.Sentry.SENTRY_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME}\"")
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}
