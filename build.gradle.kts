
import com.diffplug.spotless.LineEnding
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.MavenPublishPlugin
import com.vanniktech.maven.publish.MavenPublishPluginExtension
import groovy.util.Node
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    id(Config.QualityPlugins.spotless) version Config.QualityPlugins.spotlessVersion apply true
    jacoco
    id(Config.QualityPlugins.detekt) version Config.QualityPlugins.detektVersion
    `maven-publish`
    id(Config.QualityPlugins.binaryCompatibilityValidator) version Config.QualityPlugins.binaryCompatibilityValidatorVersion
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
        classpath(Config.BuildPlugins.composeGradlePlugin)
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
            "sentry-samples-jul",
            "sentry-samples-log4j2",
            "sentry-samples-logback",
            "sentry-samples-openfeign",
            "sentry-samples-servlet",
            "sentry-samples-spring",
            "sentry-samples-spring-jakarta",
            "sentry-samples-spring-boot",
            "sentry-samples-spring-boot-jakarta",
            "sentry-samples-spring-boot-webflux",
            "sentry-samples-spring-boot-webflux-jakarta",
            "sentry-samples-netflix-dgs",
            "sentry-uitest-android",
            "sentry-uitest-android-benchmark",
            "test-app-plain",
            "test-app-sentry"
        )
    )
}

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
                TestLogEvent.FAILED
            )
            dependsOn("cleanTest")
        }
        withType<JavaCompile> {
            options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Werror", "-Xlint:-classfile", "-Xlint:-processing"))
        }
    }
}

subprojects {
    plugins.withId(Config.QualityPlugins.detektPlugin) {
        configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = true
            config.setFrom("${rootProject.rootDir}/detekt.yml")
        }
    }

    if (!this.name.contains("sample") && !this.name.contains("integration-tests") && this.name != "sentry-test-support" && this.name != "sentry-compose-helper") {
        apply<DistributionPlugin>()

        val sep = File.separator

        configure<DistributionContainer> {
            if (this@subprojects.name.contains("-compose")) {
                this.configureForMultiplatform(this@subprojects)
            } else {
                this.getByName("main").contents {
                    // non android modules
                    from("build${sep}libs")
                    from("build${sep}publications${sep}maven")
                    // android modules
                    from("build${sep}outputs${sep}aar") {
                        include("*-release*")
                    }
                    from("build${sep}publications${sep}release")
                }
            }
            // craft only uses zip archives
            this.forEach { dist ->
                if (dist.name == DistributionPlugin.MAIN_DISTRIBUTION_NAME) {
                    tasks.getByName("distTar").enabled = false
                } else {
                    tasks.getByName(dist.name + "DistTar").enabled = false
                }
            }
        }

        tasks.named("distZip").configure {
            this.dependsOn("publishToMavenLocal")
            this.doLast {
                val distributionFilePath =
                    "${this.project.buildDir}${sep}distributions${sep}${this.project.name}-${this.project.version}.zip"
                val file = File(distributionFilePath)
                if (!file.exists()) throw IllegalStateException("Distribution file: $distributionFilePath does not exist")
                if (file.length() == 0L) throw IllegalStateException("Distribution file: $distributionFilePath is empty")
            }
        }

        afterEvaluate {
            apply<MavenPublishPlugin>()

            configure<MavenPublishPluginExtension> {
                // signing is done when uploading files to MC
                // via gpg:sign-and-deploy-file (release.kts)
                releaseSigningEnabled = false
            }

            @Suppress("UnstableApiUsage")
            configure<MavenPublishBaseExtension> {
                assignAarTypes()
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
        targetExclude("**/generated/**", "**/vendor/**")
    }
    kotlin {
        target("**/*.kt")
        ktlint()
        targetExclude("**/sentry-native/**")
    }
    kotlinGradle {
        target("**/*.kts")
        ktlint()
        targetExclude("**/sentry-native/**")
    }
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
            .filter { !it.name.contains("sample") && !it.name.contains("integration-tests") }
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

// Workaround for https://youtrack.jetbrains.com/issue/IDEA-316081/Gradle-8-toolchain-error-Toolchain-from-executable-property-does-not-match-toolchain-from-javaLauncher-property-when-different
gradle.taskGraph.whenReady {
    val task = this.allTasks.find { it.name.endsWith(".main()") } as? JavaExec
    task?.let {
        it.setExecutable(it.javaLauncher.get().executablePath.asFile.absolutePath)
    }
}
