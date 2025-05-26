plugins {
    `java-library`
    id("sentry.javadoc")
    alias(libs.plugins.buildconfig)
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

tasks.jar {
    manifest {
        attributes(
            "Sentry-Version-Name" to project.version,
            "Sentry-SDK-Name" to Config.Sentry.SENTRY_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME,
            "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-opentelemetry-agentless-spring",
            "Implementation-Vendor" to "Sentry",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}
