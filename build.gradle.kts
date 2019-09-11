import com.diffplug.spotless.LineEnding

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
        classpath("com.android.tools.build:gradle:3.1.3")
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
