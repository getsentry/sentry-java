import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    alias(libs.plugins.errorprone)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.spring.boot.three) apply false
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
    kotlinOptions.languageVersion = Config.kotlinCompatibleLanguageVersion
}

dependencies {
    api(projects.sentry)
    api(projects.sentrySpringJakarta)
    compileOnly(projects.sentryLogback)
    compileOnly(projects.sentryApacheHttpClient5)
    compileOnly(Config.Libs.springBoot3Starter)
    compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    compileOnly(projects.sentryGraphql)
    compileOnly(projects.sentryGraphql22)
    compileOnly(projects.sentryQuartz)
    compileOnly(Config.Libs.springWeb)
    compileOnly(Config.Libs.springWebflux)
    compileOnly(Config.Libs.servletApiJakarta)
    compileOnly(Config.Libs.springBoot3StarterAop)
    compileOnly(Config.Libs.springBoot3StarterSecurity)
    compileOnly(Config.Libs.springBoot3StarterGraphql)
    compileOnly(Config.Libs.springBoot3StarterQuartz)
    compileOnly(Config.Libs.reactorCore)
    compileOnly(Config.Libs.contextPropagation)
    compileOnly(Config.Libs.OpenTelemetry.otelSdk)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryCore)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    api(projects.sentryReactor)

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
    testImplementation(projects.sentryQuartz)
    testImplementation(projects.sentryGraphql)
    testImplementation(projects.sentryGraphql22)
    testImplementation(projects.sentryApacheHttpClient5)
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.okhttp.mockwebserver)

    testImplementation(Config.Libs.okhttp)
    testImplementation(Config.Libs.springBoot3Starter)
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(Config.Libs.springBoot3StarterTest)
    testImplementation(Config.Libs.springBoot3StarterWeb)
    testImplementation(Config.Libs.springBoot3StarterWebflux)
    testImplementation(Config.Libs.springBoot3StarterSecurity)
    testImplementation(Config.Libs.springBoot3StarterAop)
    testImplementation(Config.Libs.springBoot3StarterQuartz)
    testImplementation(Config.Libs.springBoot3StarterGraphql)
    testImplementation(Config.Libs.contextPropagation)
    testImplementation(Config.Libs.OpenTelemetry.otelSdk)
    testImplementation(Config.Libs.OpenTelemetry.otelExtensionAutoconfigureSpi)
    testImplementation(Config.Libs.springBoot3StarterOpenTelemetry)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryCore)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgent)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
    testImplementation(projects.sentryReactor)
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
    packageName("io.sentry.spring.boot.jakarta")
    buildConfigField("String", "SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME", "\"${Config.Sentry.SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME}\"")
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
            "Sentry-SDK-Name" to Config.Sentry.SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME,
            "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-spring-boot-jakarta",
            "Implementation-Vendor" to "Sentry",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}
