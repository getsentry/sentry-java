import com.diffplug.spotless.LineEnding
import com.vanniktech.maven.publish.MavenPublishPlugin
import com.vanniktech.maven.publish.MavenPublishPluginExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    id(Config.QualityPlugins.spotless) version Config.QualityPlugins.spotlessVersion apply true
    jacoco
    id(Config.QualityPlugins.detekt) version Config.QualityPlugins.detektVersion
    `maven-publish`
}

buildscript {
    repositories {
        google()
        jcenter()
        maven { setUrl("https://kotlin.bintray.com/kotlinx") }
    }
    dependencies {
        classpath(Config.BuildPlugins.androidGradle)
        classpath(kotlin(Config.BuildPlugins.kotlinGradlePlugin, version = Config.kotlinVersion))
        classpath(Config.BuildPlugins.gradleMavenPublishPlugin)
        // dokka is required by gradle-maven-publish-plugin.
        classpath(Config.BuildPlugins.dokkaPlugin)
        classpath(Config.QualityPlugins.errorpronePlugin)
        classpath(Config.QualityPlugins.gradleVersionsPlugin)

        // add classpath of androidNativeBundle
        // com.ydq.android.gradle.build.tool:nativeBundle:{version}}
        classpath(Config.NativePlugins.nativeBundlePlugin)

        // add classpath of sentry android gradle plugin
        // classpath("io.sentry:sentry-android-gradle-plugin:{version}")

        classpath(Config.QualityPlugins.binaryCompatibilityValidatorPlugin)
    }
}

apply(plugin = Config.QualityPlugins.binaryCompatibilityValidator)

allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
    }
    group = Config.Sentry.group
    version = properties[Config.Sentry.versionNameProp].toString()
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
            options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Werror", "-Xlint:-classfile", "-Xlint:-processing"))
        }
    }
}

subprojects {
    if (!this.name.contains("sample") && this.name != "sentry-test-support") {
        apply<DistributionPlugin>()

        configure<DistributionContainer> {
            this.getByName("main").contents {
                // non android modules
                from("build/libs")
                from("build/publications/maven")
                // android modules
                from("build/outputs/aar")
                from("build/publications/release")
            }
        }
        tasks.named("distZip").configure {
            this.dependsOn("publishToMavenLocal")
            this.doLast {
                val distributionFilePath = "${this.project.buildDir}/distributions/${this.project.name}-${this.project.version}.zip"
                val file = File(distributionFilePath)
                if (!file.exists()) throw IllegalStateException("Distribution file: $distributionFilePath does not exist")
                if (file.length() == 0L) throw IllegalStateException("Distribution file: $distributionFilePath is empty")
            }
        }
        afterEvaluate {
            apply<MavenPublishPlugin>()

            configure<MavenPublishPluginExtension> {
                val sign = Config.BuildPlugins.shouldSignArtifacts(project.version.toString())
                releaseSigningEnabled = sign
                nexus {
                    stagingProfile = Config.Sentry.group
                }
            }

            // signing info and maven central info go to:
            // ~/.gradle/gradle.properties

            // signing info:
            // signing.keyId=id
            // signing.password=password
            // signing.secretKeyRingFile=file path

            // maven central info:
            // mavenCentralRepositoryUsername=user name
            // mavenCentralRepositoryPassword=password
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
        target("**/*.kt")
        ktlint()
    }
    kotlinGradle {
        target("**/*.kts")
        ktlint()
    }
}

tasks.named("build") {
    dependsOn(":spotlessApply")
}

gradle.projectsEvaluated {
    tasks.create("aggregateJavadocs", Javadoc::class.java) {
        setDestinationDir(file("$buildDir/docs/javadoc"))
        title = "${project.name} $version API"
        val opts = options as StandardJavadocDocletOptions
        opts.quiet()
        opts.encoding = "UTF-8"
        opts.memberLevel = JavadocMemberLevel.PROTECTED
        opts.stylesheetFile(file("$projectDir/docs/stylesheet.css"))
        opts.links = listOf(
            "https://docs.oracle.com/javase/8/docs/api/",
            "https://docs.spring.io/spring-framework/docs/current/javadoc-api/",
            "https://docs.spring.io/spring-boot/docs/current/api/"
        )
        subprojects
            .filter { !it.name.contains("sample") }
            .forEach { proj ->
                proj.tasks.withType<Javadoc>().forEach { javadocTask ->
                    source += javadocTask.source
                    classpath += javadocTask.classpath
                    excludes += javadocTask.excludes
                    includes += javadocTask.includes
                }
            }
    }
}
