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
    kotlinOptions.languageVersion = libs.versions.kotlin.compatible.version.get()
}

dependencies {
    api(projects.sentry)
    api(projects.sentryKotlinExtensions)

    compileOnly(libs.apollo3.kotlin)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.nopen.annotations)
    errorprone(libs.errorprone.core)
    errorprone(libs.nopen.checker)
    errorprone(libs.nullaway)

    // tests
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(libs.apollo3.kotlin)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.okhttp.mockwebserver)
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
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
    }
}

buildConfig {
    useJavaOutput()
    packageName("io.sentry.apollo3")
    buildConfigField("String", "SENTRY_APOLLO3_SDK_NAME", "\"${Config.Sentry.SENTRY_APOLLO3_SDK_NAME}\"")
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}
