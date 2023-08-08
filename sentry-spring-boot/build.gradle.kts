import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
    id(Config.BuildPlugins.buildConfig) version Config.BuildPlugins.buildConfigVersion
    id(Config.BuildPlugins.springBoot) version Config.springBootVersion apply false
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
    api(projects.sentrySpring)
    compileOnly(projects.sentryLogback)
    compileOnly(projects.sentryApacheHttpClient5)
    compileOnly(Config.Libs.springBootStarter)
    compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    compileOnly(Config.Libs.springWeb)
    compileOnly(Config.Libs.springWebflux)
    compileOnly(Config.Libs.servletApi)
    compileOnly(Config.Libs.springBootStarterAop)
    compileOnly(Config.Libs.springBootStarterSecurity)
    compileOnly(Config.Libs.springBootStarterGraphql)
    compileOnly(Config.Libs.reactorCore)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryCore)
    compileOnly(projects.sentryGraphql)

    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
    annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(projects.sentryLogback)
    testImplementation(projects.sentryApacheHttpClient5)
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.TestLibs.mockWebserver)
    testImplementation(Config.Libs.okhttp)
    testImplementation(Config.Libs.springBootStarter)
    testImplementation(Config.Libs.springBootStarterTest)
    testImplementation(Config.Libs.springBootStarterWeb)
    testImplementation(Config.Libs.springBootStarterWebflux)
    testImplementation(Config.Libs.springBootStarterSecurity)
    testImplementation(Config.Libs.springBootStarterAop)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryCore)
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

buildConfig {
    useJavaOutput()
    packageName("io.sentry.spring.boot")
    buildConfigField("String", "SENTRY_SPRING_BOOT_SDK_NAME", "\"${Config.Sentry.SENTRY_SPRING_BOOT_SDK_NAME}\"")
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

val generateBuildConfig by tasks
tasks.withType<JavaCompile>().configureEach {
    dependsOn(generateBuildConfig)
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
    }
}
