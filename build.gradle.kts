import com.diffplug.spotless.LineEnding
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    java
    id("com.diffplug.gradle.spotless") version "3.24.2" apply true
    jacoco
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

buildscript {
    repositories {
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.50")
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
    }
    group = "io.sentry"
    version = "2.0.0-SNAPSHOT"
    tasks {
        withType<Test> {
            testLogging.showStandardStreams = true
            testLogging.exceptionFormat = TestExceptionFormat.FULL
            testLogging.events = setOf(
                TestLogEvent.SKIPPED,
                TestLogEvent.PASSED,
                TestLogEvent.FAILED)
            dependsOn("cleanTest")
        }
    }
}

spotless {
    lineEndings = LineEnding.UNIX
    java {
        target("**/*.java")
        removeUnusedImports()
        googleJavaFormat()
    }

    kotlin {
        ktlint()
        target("**/*.kt")
    }
    kotlinGradle {
        ktlint()
    }
}
