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
}

dependencies {
    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)
    errorprone(Config.CompileOnly.errorProneNullAway)
    // tests
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.TestLibs.mockitoInline)
    testImplementation(Config.TestLibs.awaitility)
    testImplementation(Config.TestLibs.javaFaker)
    testImplementation(Config.TestLibs.msgpack)
    testImplementation(projects.sentryTestSupport)
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

val generateBuildConfig by tasks
tasks.withType<JavaCompile>() {
    dependsOn(generateBuildConfig)
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
    }
    options.errorprone.errorproneArgs.add("-XepExcludedPaths:.*/io/sentry/vendor/.*")
}
