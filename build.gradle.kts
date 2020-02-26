import com.diffplug.spotless.LineEnding
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    id(Config.QualityPlugins.spotless) version Config.QualityPlugins.spotlessVersion apply true
    jacoco
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

buildscript {
    repositories {
        google()
        jcenter()
        maven { setUrl("https://dl.bintray.com/maranda/maven/") }
    }
    dependencies {
        classpath(Config.BuildPlugins.androidGradle)
        classpath(kotlin(Config.BuildPlugins.kotlinGradlePlugin, version = Config.kotlinVersion))
        classpath(Config.QualityPlugins.errorpronePlugin)
        classpath(Config.Deploy.novodaBintrayPlugin)
        classpath(Config.QualityPlugins.gradleVersionsPlugin)

        // add classpath of androidNativeBundle
        // com.ydq.android.gradle.build.tool:nativeBundle:{version}}
        classpath(Config.NativePlugins.nativeBundlePlugin)

        // add classpath of sentry android gradle plugin
        // classpath("io.sentry:sentry-android-gradle-plugin:{version}")
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
    }
    group = Config.Sentry.group
    version = Config.Sentry.version
    description = Config.Sentry.description
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
        withType<JavaCompile> {
            options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Werror", "-Xlint:-classfile"))
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

tasks.named("build") {
    dependsOn(":spotlessApply")
}
