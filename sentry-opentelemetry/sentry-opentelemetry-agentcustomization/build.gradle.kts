import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    id("io.sentry.javadoc")
    kotlin("jvm")
    jacoco
    alias(libs.plugins.errorprone)
    alias(libs.plugins.gradle.versions)
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

dependencies {
    compileOnly(projects.sentry)
    implementation(projects.sentryOpentelemetry.sentryOpentelemetryCore) {
        exclude(group = "io.opentelemetry")
        exclude(group = "io.opentelemetry.javaagent")
    }
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.nopen.annotations)
    compileOnly(libs.otel)
    compileOnly(libs.otel.extension.autoconfigure.spi)
    compileOnly(libs.otel.javaagent.extension.api)
    compileOnly(libs.otel.javaagent.tooling)

    errorprone(libs.errorprone.core)
    errorprone(libs.nopen.checker)
    errorprone(libs.nullaway)

    // tests
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
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

tasks.jar {
    manifest {
        attributes(
            "Sentry-Version-Name" to project.version,
            "Sentry-SDK-Name" to Config.Sentry.SENTRY_OPENTELEMETRY_AGENTCUSTOMIZATION_SDK_NAME,
            "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-opentelemetry-agentcustomization",
            "Implementation-Vendor" to "Sentry",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}
