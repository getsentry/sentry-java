import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import groovy.util.Node
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import kotlinx.kover.gradle.plugin.dsl.KoverReportExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

plugins {
    `java-library`
    alias(libs.plugins.spotless) apply false
    jacoco
    alias(libs.plugins.detekt)
    `maven-publish`
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.jacoco.android) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.buildconfig) apply false
    // dokka is required by gradle-maven-publish-plugin.
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.dokka.javadoc) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.errorprone) apply false
    alias(libs.plugins.gradle.versions) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    id("io.sentry.javadoc.aggregate")
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
            "sentry-samples-spring-boot-jakarta-opentelemetry",
            "sentry-samples-spring-boot-jakarta-opentelemetry-noagent",
            "sentry-samples-spring-boot-webflux",
            "sentry-samples-spring-boot-webflux-jakarta",
            "sentry-samples-spring-boot-4",
            "sentry-samples-spring-boot-4-opentelemetry",
            "sentry-samples-spring-boot-4-opentelemetry-noagent",
            "sentry-samples-spring-boot-4-webflux",
            "sentry-samples-ktor-client",
            "sentry-uitest-android",
            "sentry-uitest-android-benchmark",
            "sentry-uitest-android-critical",
            "test-app-plain",
            "test-app-sentry",
            "sentry-samples-netflix-dgs"
        )
    )
}

allprojects {
    group = Config.Sentry.group
    version = properties[Config.Sentry.versionNameProp].toString()
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

            // Cap JVM args per test
            minHeapSize = "256m"
            maxHeapSize = "2g"
        }
        withType<JavaCompile>().configureEach {
            options.compilerArgs.addAll(arrayOf("-Xlint:all", "-Werror", "-Xlint:-classfile", "-Xlint:-processing", "-Xlint:-try"))
        }
    }
}

subprojects {
    apply { plugin("io.sentry.spotless") }

    val jacocoAndroidModules = listOf(
        "sentry-android-core",
        "sentry-android-fragment",
        "sentry-android-navigation",
        "sentry-android-ndk",
        "sentry-android-sqlite",
        "sentry-android-replay",
        "sentry-android-timber"
    )
    if (jacocoAndroidModules.contains(name)) {
        afterEvaluate {
            jacoco {
                toolVersion = "0.8.10"
            }

            tasks.withType<Test>().configureEach {
                configure<JacocoTaskExtension> {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
        }
    }

    val koverKmpModules = listOf("sentry-compose")
    if (koverKmpModules.contains(name)) {
        afterEvaluate {
            configure<KoverReportExtension> {
                androidReports("release") {
                    xml {
                        // Change the report file name so the Codecov Github action can find it
                        setReportFile(project.layout.buildDirectory.file("reports/kover/report.xml").get().asFile)
                    }
                }
            }
        }
    }

    plugins.withId(Config.QualityPlugins.detektPlugin) {
        configure<DetektExtension> {
            buildUponDefaultConfig = true
            allRules = true
            config.setFrom("${rootProject.rootDir}/detekt.yml")
        }
    }

    if (!this.name.contains("sample") && !this.name.contains("integration-tests") && this.name != "sentry-system-test-support" && this.name != "sentry-test-support") {
        apply<DistributionPlugin>()
        apply<com.vanniktech.maven.publish.MavenPublishPlugin>()

        val sep = File.separator

        configure<DistributionContainer> {
            if (this@subprojects.name.contains("-compose")) {
                this.configureForMultiplatform(this@subprojects)
            } else {
                this.configureForJvm(this@subprojects)
            }
            // craft only uses zip archives
            this.forEach { dist ->
                if (dist.name == DistributionPlugin.MAIN_DISTRIBUTION_NAME) {
                    tasks.named("distTar").configure { enabled = false }
                } else {
                    tasks.named(dist.name + "DistTar").configure { enabled = false }
                }
            }
        }

        tasks.named("distZip").configure {
            this.dependsOn("publishToMavenLocal")
            val file = this.project.layout.buildDirectory.file("distributions${sep}${this.project.name}-${this.project.version}.zip").get().asFile
            this.doLast {
                if (!file.exists()) throw IllegalStateException("Distribution file: ${file.absolutePath} does not exist")
                if (file.length() == 0L) throw IllegalStateException("Distribution file: ${file.absolutePath} is empty")
            }
        }

        plugins.withId("java-library") {
            configure<MavenPublishBaseExtension> {
                // we have to disable javadoc publication in maven-publish plugin as it's not
                // including it in the .module file https://github.com/vanniktech/gradle-maven-publish-plugin/issues/861
                // and do it ourselves
                configure(JavaLibrary(JavadocJar.None(), sourcesJar = true))
            }

            configure<JavaPluginExtension> {
                withJavadocJar()

                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
        }

        afterEvaluate {
            apply<MavenPublishPlugin>()

            configure<MavenPublishBaseExtension> {
                assignAarTypes()
            }

            // this is needed for sentry-unity to consume our artifacts locally as proper maven publication
            configure<PublishingExtension> {
                repositories {
                    maven {
                        name = "unityMaven"
                        url = rootProject.layout.buildDirectory.file("unityMaven").get().asFile.toURI()
                    }
                }
            }

            // maven central info go to:
            // ~/.gradle/gradle.properties

            // maven central info:
            // mavenCentralUsername=user name
            // mavenCentralPassword=password
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
                this.dependsOn(proj.tasks.findByName("compileReleaseUnitTestSources"))
            } else {
                this.dependsOn(proj.tasks.findByName("testClasses"))
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

/*
 * Adapted from https://github.com/androidx/androidx/blob/c799cba927a71f01ea6b421a8f83c181682633fb/buildSrc/private/src/main/kotlin/androidx/build/MavenUploadHelper.kt#L524-L549
 *
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Workaround for https://github.com/gradle/gradle/issues/3170
fun MavenPublishBaseExtension.assignAarTypes() {
    pom {
        withXml {
            val dependencies = asNode().children().find {
                it is Node && it.name().toString().endsWith("dependencies")
            } as Node?

            dependencies?.children()?.forEach { dep ->
                if (dep !is Node) {
                    return@forEach
                }
                val group = dep.children().firstOrNull {
                    it is Node && it.name().toString().endsWith("groupId")
                } as? Node
                val groupValue = group?.children()?.firstOrNull() as? String

                val artifactId = dep.children().firstOrNull {
                    it is Node && it.name().toString().endsWith("artifactId")
                } as? Node
                val artifactIdValue = artifactId?.children()?.firstOrNull() as? String

                if (artifactIdValue in Config.BuildScript.androidLibs) {
                    dep.appendNode("type", "aar")
                } else if ("$groupValue:$artifactIdValue" in Config.BuildScript.androidXLibs) {
                    dep.appendNode("type", "aar")
                }
            }
        }
    }
}
