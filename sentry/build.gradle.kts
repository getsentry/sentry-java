import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    id("io.sentry.javadoc")
    kotlin("jvm")
    jacoco
    alias(libs.plugins.errorprone)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.buildconfig)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

dependencies {
    errorprone(libs.errorprone.core)
    errorprone(libs.nopen.checker)
    errorprone(libs.nullaway)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.nopen.annotations)
    // tests
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.javafaker)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.msgpack)
    testImplementation(libs.okio)
    testImplementation(projects.sentryTestSupport)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(false)
    }
}

tasks {
    jacocoTestCoverageVerification {
        violationRules {
            rule { limit { minimum = Config.QualityPlugins.Jacoco.minimumCoverage } }
        }
    }
    check {
        dependsOn(jacocoTestCoverageVerification)
        dependsOn(jacocoTestReport)
    }
    test {
        environment["SENTRY_TEST_PROPERTY"] = "\"some-value\""
        environment["SENTRY_TEST_MAP_KEY1"] = "\"value1\""
        environment["SENTRY_TEST_MAP_KEY2"] = "value2"
    }
}

buildConfig {
    useJavaOutput()
    packageName("io.sentry")
    buildConfigField("String", "SENTRY_JAVA_SDK_NAME", "\"${Config.Sentry.SENTRY_JAVA_SDK_NAME}\"")
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(tasks.generateBuildConfig)
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
    }
    options.errorprone.errorproneArgs.add("-XepExcludedPaths:.*/io/sentry/vendor/.*")
}

tasks.jar {
    manifest {
        attributes(
            "Sentry-Version-Name" to project.version,
            "Sentry-SDK-Name" to Config.Sentry.SENTRY_JAVA_SDK_NAME,
            "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry",
            "Implementation-Vendor" to "Sentry",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}
