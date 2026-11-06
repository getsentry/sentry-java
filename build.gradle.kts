import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.detekt)
    `maven-publish`
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
