enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "sentry-root"
rootProject.buildFileName = "build.gradle.kts"

include(
    "sentry",
    "sentry-kotlin-extensions",
    "sentry-android-core",
    "sentry-android-ndk",
    "sentry-android",
    "sentry-android-timber",
    "sentry-android-okhttp",
    "sentry-android-fragment",
    "sentry-apollo",
    "sentry-test-support",
    "sentry-log4j2",
    "sentry-logback",
    "sentry-jul",
    "sentry-servlet",
    "sentry-apache-http-client-5",
    "sentry-spring",
    "sentry-spring-boot-starter",
    "sentry-bom",
    "sentry-openfeign",
    "sentry-graphql-java",
    "sentry-samples:sentry-samples-android",
    "sentry-samples:sentry-samples-console",
    "sentry-samples:sentry-samples-jul",
    "sentry-samples:sentry-samples-log4j2",
    "sentry-samples:sentry-samples-logback",
    "sentry-samples:sentry-samples-openfeign",
    "sentry-samples:sentry-samples-servlet",
    "sentry-samples:sentry-samples-spring",
    "sentry-samples:sentry-samples-spring-boot",
    "sentry-samples:sentry-samples-spring-boot-webflux"
)

gradle.beforeProject {
    if (project.name == "sentry-android-ndk" || project.name == "sentry-samples-android") {
        exec {
            logger.log(LogLevel.LIFECYCLE, "Initializing git submodules")
            commandLine("git", "submodule", "update", "--init", "--recursive")
        }
    }
}
