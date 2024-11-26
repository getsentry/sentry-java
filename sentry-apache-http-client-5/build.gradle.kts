import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
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
    api(Config.Libs.apacheHttpClient)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(Config.Libs.apacheHttpClient)
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.sentry.apache.http.client.5")
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
