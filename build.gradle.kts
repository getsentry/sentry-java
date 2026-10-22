import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt)
    `maven-publish`
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.buildconfig) apply false
    // dokka is applied directly by sentry-compose; pinned here so it is on the classpath.
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.dokka.javadoc) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.gradle.versions) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    id("io.sentry.javadoc.aggregate")
    alias(libs.plugins.sentry) apply false
}

check(JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
    "This build requires JDK 21 or newer, but runs on JDK ${JavaVersion.current()}."
}

buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath(Config.BuildPlugins.androidGradle)

        // add classpath of sentry android gradle plugin
        // classpath("io.sentry:sentry-android-gradle-plugin:{version}")

        classpath(libs.commons.compress)
    }
}

apiValidation {
    ignoredPackages.addAll(
        setOf(
            "io.sentry.android.core.internal"
        )
    )
    ignoredProjects.addAll(
        listOf(
            "sentry-samples-android",
            "sentry-samples-console",
            "sentry-samples-console-opentelemetry-noagent",
            "sentry-samples-jul",
            "sentry-samples-log4j2",
            "sentry-samples-logback",
            "sentry-samples-openfeign",
            "sentry-samples-servlet",
            "sentry-samples-spring",
            "sentry-samples-spring-jakarta",
            "sentry-samples-spring-7",
            "sentry-samples-spring-boot",
            "sentry-samples-spring-boot-opentelemetry",
            "sentry-samples-spring-boot-opentelemetry-noagent",
            "sentry-samples-spring-boot-jakarta",
            "sentry-samples-spring-boot-jakarta-log4j2",
            "sentry-samples-spring-boot-jakarta-opentelemetry",
            "sentry-samples-spring-boot-jakarta-opentelemetry-noagent",
            "sentry-samples-spring-boot-webflux",
            "sentry-samples-spring-boot-webflux-jakarta",
            "sentry-samples-spring-boot-4",
            "sentry-samples-spring-boot-4-log4j2",
            "sentry-samples-spring-boot-4-opentelemetry",
            "sentry-samples-spring-boot-4-opentelemetry-noagent",
            "sentry-samples-spring-boot-4-otlp",
            "sentry-samples-spring-boot-4-webflux",
            "sentry-samples-ktor-client",
            "sentry-uitest-android",
            "sentry-uitest-android-critical",
            "test-app-size",
            "sentry-samples-netflix-dgs",
            "sentry-samples-console-otlp",
            "sentry-test-support",
            "sentry-system-test-support"
        )
    )
}

allprojects {
    group = Config.Sentry.group
    version = providers.gradleProperty(Config.Sentry.versionNameProp).get()
    description = Config.Sentry.description
    tasks {
        withType<Test>().configureEach {
            testLogging.showStandardStreams = true
            testLogging.exceptionFormat = TestExceptionFormat.FULL
            testLogging.events = setOf(
                TestLogEvent.SKIPPED,
                TestLogEvent.PASSED,
                TestLogEvent.FAILED
            )
        }
        withType<JavaCompile>().configureEach {
            options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Werror", "-Xlint:-classfile", "-Xlint:-processing", "-Xlint:-try", "-Xlint:-options"))
        }
    }
}

tasks.register("buildForCodeQL") {
    subprojects
        .filter {
            !it.displayName.contains("sample") &&
                !it.displayName.contains("integration-tests") &&
                !it.displayName.contains("bom") &&
                it.name != "sentry-opentelemetry"
        }
        .forEach { proj ->
            if (proj.plugins.hasPlugin("com.android.library")) {
                proj.tasks.findByName("compileReleaseUnitTestSources")?.let { testTask ->
                    this.dependsOn(testTask)
                }
            } else {
                proj.tasks.findByName("testClasses")?.let { testTask ->
                    this.dependsOn(testTask)
                }
            }
        }
}
