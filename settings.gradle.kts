enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "sentry-root"
rootProject.buildFileName = "build.gradle.kts"
includeBuild("build-logic")
include(
    "sentry",
    "sentry-kotlin-extensions",
    "sentry-android-core",
    "sentry-android-ndk",
    "sentry-android",
    "sentry-android-timber",
    "sentry-android-fragment",
    "sentry-android-navigation",
    "sentry-android-sqlite",
    "sentry-android-replay",
    "sentry-compose",
    "sentry-apollo",
    "sentry-apollo-3",
    "sentry-apollo-4",
    "sentry-system-test-support",
    "sentry-test-support",
    "sentry-log4j2",
    "sentry-logback",
    "sentry-jul",
    "sentry-servlet",
    "sentry-servlet-jakarta",
    "sentry-apache-http-client-5",
    "sentry-spring",
    "sentry-spring-jakarta",
    "sentry-spring-boot",
    "sentry-spring-boot-jakarta",
    "sentry-spring-boot-starter",
    "sentry-spring-boot-starter-jakarta",
    "sentry-bom",
    "sentry-openfeign",
    "sentry-graphql",
    "sentry-graphql-22",
    "sentry-graphql-core",
    "sentry-jdbc",
    "sentry-opentelemetry:sentry-opentelemetry-bootstrap",
    "sentry-opentelemetry:sentry-opentelemetry-core",
    "sentry-opentelemetry:sentry-opentelemetry-agentcustomization",
    "sentry-opentelemetry:sentry-opentelemetry-agent",
    "sentry-opentelemetry:sentry-opentelemetry-agentless",
    "sentry-opentelemetry:sentry-opentelemetry-agentless-spring",
    "sentry-quartz",
    "sentry-okhttp",
    "sentry-reactor",
    "sentry-ktor-client",
    "sentry-samples:sentry-samples-android",
    "sentry-samples:sentry-samples-console",
    "sentry-samples:sentry-samples-console-opentelemetry-noagent",
    "sentry-samples:sentry-samples-jul",
    "sentry-samples:sentry-samples-ktor-client",
    "sentry-samples:sentry-samples-log4j2",
    "sentry-samples:sentry-samples-logback",
    "sentry-samples:sentry-samples-servlet",
    "sentry-samples:sentry-samples-spring",
    "sentry-samples:sentry-samples-spring-jakarta",
    "sentry-samples:sentry-samples-spring-boot",
    "sentry-samples:sentry-samples-spring-boot-opentelemetry",
    "sentry-samples:sentry-samples-spring-boot-opentelemetry-noagent",
    "sentry-samples:sentry-samples-spring-boot-jakarta",
    "sentry-samples:sentry-samples-spring-boot-jakarta-opentelemetry",
    "sentry-samples:sentry-samples-spring-boot-jakarta-opentelemetry-noagent",
    "sentry-samples:sentry-samples-spring-boot-webflux",
    "sentry-samples:sentry-samples-spring-boot-webflux-jakarta",
    "sentry-samples:sentry-samples-netflix-dgs",
    "sentry-android-integration-tests:sentry-uitest-android-critical",
    "sentry-android-integration-tests:sentry-uitest-android-benchmark",
    "sentry-android-integration-tests:sentry-uitest-android",
    "sentry-android-integration-tests:test-app-plain",
    "sentry-android-integration-tests:test-app-sentry",
    "sentry-samples:sentry-samples-openfeign"
)
