plugins {
    `java-library`
    java
    id("com.diffplug.gradle.spotless") version "3.24.2" apply true
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
    lineEndings('UNIX')
    java {
        target("**/*.java")
        removeUnusedImports()
        googleJavaFormat()
    }
}