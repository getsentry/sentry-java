import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
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

    compileOnly(Config.Libs.OpenTelemetry.otelSdk)
    compileOnly(Config.Libs.OpenTelemetry.otelExtensionAutoconfigureSpi)
    compileOnly(Config.Libs.OpenTelemetry.otelJavaAgentExtensionApi)
    compileOnly(Config.Libs.OpenTelemetry.otelJavaAgentTooling)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)
    errorprone(Config.CompileOnly.errorProneNullAway)

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
    toolVersion = Config.QualityPlugins.Jacoco.version
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
