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

        val sep = File.separator

        configure<DistributionContainer> {
            this.getByName("main").contents {
                // non android modules
                from("build${sep}libs")
                from("build${sep}publications${sep}maven")
                // android modules
                from("build${sep}outputs${sep}aar")
                from("build${sep}publications${sep}release")
            }
        }

        val rootDir = this.project.rootProject.projectDir
        val distDir = File("${rootDir}${sep}distributions")

        // create dir if it does not exist
        distDir.mkdirs()

        tasks.named("distZip").configure {
            this.dependsOn("publishToMavenLocal")
            this.doLast {
                val distributionFilePath = "${this.project.buildDir}${sep}distributions${sep}${this.project.name}-${this.project.version}.zip"
                val file = File(distributionFilePath)
                if (!file.exists()) throw IllegalStateException("Distribution file: $distributionFilePath does not exist")
                if (file.length() == 0L) throw IllegalStateException("Distribution file: $distributionFilePath is empty")

                val newFile = File("${distDir}${sep}${file.name}")
                file.copyTo(newFile, overwrite = true)

                UnzipUtils.unzip(newFile, distDir.path)
                newFile.delete()
            }
        }

        afterEvaluate {
            apply<MavenPublishPlugin>()

            configure<MavenPublishPluginExtension> {
                // signing is done when uploading files to MC
                // via gpg:sign-and-deploy-file (release.kts)
                releaseSigningEnabled = false
            }

            // maven central info go to:
            // ~/.gradle/gradle.properties

            // maven central info:
            // mavenCentralUsername=user name
            // mavenCentralPassword=password
        }
    }
}

spotless {
    lineEndings = LineEnding.UNIX
    java {
        target("**/*.java")
        removeUnusedImports()
        googleJavaFormat()
        targetExclude("**/generated/**")
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
