import com.diffplug.spotless.LineEnding

plugins {
    `java-library`
    java
    id("com.diffplug.gradle.spotless") version "3.24.2" apply true
    jacoco
}

group = "io.sentry"
version = "2.0.0-SNAPSHOT"

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

allprojects {
    repositories {
        jcenter()
        mavenCentral()
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
        // optionally takes a version
        ktlint()
        target("**/*.kt")
    }
    kotlinGradle {
        // same as kotlin, but for .gradle.kts files (defaults to '*.gradle.kts')
        ktlint()
    }
}