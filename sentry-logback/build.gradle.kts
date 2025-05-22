import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
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
    api(projects.sentry)
    compileOnly(Config.Libs.logbackClassic)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(Config.Libs.logbackClassic)
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

buildConfig {
    useJavaOutput()
    packageName("io.sentry.logback")
    buildConfigField("String", "SENTRY_LOGBACK_SDK_NAME", "\"${Config.Sentry.SENTRY_LOGBACK_SDK_NAME}\"")
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(tasks.generateBuildConfig)
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Sentry-Version-Name" to project.version,
            "Sentry-SDK-Name" to Config.Sentry.SENTRY_LOGBACK_SDK_NAME,
            "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-logback",
            "Implementation-Vendor" to "Sentry",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}
