import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
    id(Config.BuildPlugins.buildConfig) version Config.BuildPlugins.buildConfigVersion
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    kotlinOptions.languageVersion = Config.kotlinCompatibleLanguageVersion
}

dependencies {
    api(projects.sentry)
    compileOnly(Config.Libs.quartz)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(projects.sentry)
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.TestLibs.mockitoInline)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.sentry.quartz")
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

buildConfig {
    useJavaOutput()
    packageName("io.sentry.quartz")
    buildConfigField("String", "SENTRY_QUARTZ_SDK_NAME", "\"${Config.Sentry.SENTRY_QUARTZ_SDK_NAME}\"")
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}
