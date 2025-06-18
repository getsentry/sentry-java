plugins {
    `java-library`
    id("io.sentry.javadoc")
    alias(libs.plugins.buildconfig)
}

dependencies {
    api(projects.sentry)
    implementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
    implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)

    api(libs.otel)
    api(libs.otel.semconv)
    api(libs.otel.semconv.incubating)
    api(libs.otel.extension.autoconfigure)
    api(libs.springboot3.otel)
}

buildConfig {
    useJavaOutput()
    packageName("io.sentry.opentelemetry.agentless.spring")
    buildConfigField(
        "String",
        "SENTRY_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME",
        "\"${Config.Sentry.SENTRY_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME}\"",
    )
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
            "Implementation-Version" to project.version,
        )
    }
}
