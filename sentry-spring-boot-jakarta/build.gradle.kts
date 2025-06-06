import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    `java-library`
    id("io.sentry.javadoc")
    kotlin("jvm")
    jacoco
    alias(libs.plugins.errorprone)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.buildconfig)
    alias(libs.plugins.springboot3) apply false
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
    kotlinOptions.languageVersion = libs.versions.kotlin.compatible.version.get()
}

dependencies {
    api(projects.sentry)
    api(projects.sentrySpringJakarta)
    compileOnly(projects.sentryLogback)
    compileOnly(projects.sentryApacheHttpClient5)
    compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    compileOnly(projects.sentryGraphql)
    compileOnly(projects.sentryGraphql22)
    compileOnly(projects.sentryQuartz)
    compileOnly(Config.Libs.springWeb)
    compileOnly(Config.Libs.springWebflux)
    compileOnly(libs.context.propagation)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.nopen.annotations)
    compileOnly(libs.otel)
    compileOnly(libs.reactor.core)
    compileOnly(libs.servlet.jakarta.api)
    compileOnly(libs.springboot3.starter)
    compileOnly(libs.springboot3.starter.aop)
    compileOnly(libs.springboot3.starter.graphql)
    compileOnly(libs.springboot3.starter.quartz)
    compileOnly(libs.springboot3.starter.security)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryCore)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    api(projects.sentryReactor)

    annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
    annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
    annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

    errorprone(libs.errorprone.core)
    errorprone(libs.nopen.checker)
    errorprone(libs.nullaway)

    // tests
    testImplementation(projects.sentryLogback)
    testImplementation(projects.sentryApacheHttpClient5)
    testImplementation(projects.sentryGraphql)
    testImplementation(projects.sentryGraphql22)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryCore)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgent)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
    testImplementation(projects.sentryQuartz)
    testImplementation(projects.sentryReactor)
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testImplementation(libs.context.propagation)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.otel)
    testImplementation(libs.otel.extension.autoconfigure.spi)
    testImplementation(libs.springboot3.otel)
    testImplementation(libs.springboot3.starter)
    testImplementation(libs.springboot3.starter.aop)
    testImplementation(libs.springboot3.starter.graphql)
    testImplementation(libs.springboot3.starter.quartz)
    testImplementation(libs.springboot3.starter.security)
    testImplementation(libs.springboot3.starter.test)
    testImplementation(libs.springboot3.starter.web)
    testImplementation(libs.springboot3.starter.webflux)
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
