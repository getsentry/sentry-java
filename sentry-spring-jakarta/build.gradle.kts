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
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
        languageVersion = Config.kotlinCompatibleLanguageVersion
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

dependencies {
    api(projects.sentry)
    compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    compileOnly(Config.Libs.springWeb)
    compileOnly(Config.Libs.springAop)
    compileOnly(Config.Libs.springSecurityWeb)
    compileOnly(Config.Libs.springBoot3StarterGraphql)
    compileOnly(Config.Libs.springBoot3StarterQuartz)
    compileOnly(Config.Libs.aspectj)
    compileOnly(Config.Libs.servletApiJakarta)
    compileOnly(Config.Libs.slf4jApi)
    compileOnly(Config.Libs.contextPropagation)
    compileOnly(Config.Libs.OpenTelemetry.otelSdk)

    compileOnly(Config.Libs.springWebflux)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)
    compileOnly(projects.sentryGraphql)
    compileOnly(projects.sentryGraphql22)
    compileOnly(projects.sentryQuartz)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
    api(projects.sentryReactor)

    // tests
    testImplementation(projects.sentryTestSupport)
    testImplementation(projects.sentryGraphql)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)
    testImplementation(Config.Libs.springBoot3StarterTest)
    testImplementation(Config.Libs.springBoot3StarterWeb)
    testImplementation(Config.Libs.springBoot3StarterWebflux)
    testImplementation(Config.Libs.springBoot3StarterSecurity)
    testImplementation(Config.Libs.springBoot3StarterAop)
    testImplementation(Config.Libs.springBoot3StarterGraphql)
    testImplementation(Config.Libs.contextPropagation)
    testImplementation(Config.Libs.graphQlJava22)
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
    packageName("io.sentry.spring.jakarta")
    buildConfigField("String", "SENTRY_SPRING_JAKARTA_SDK_NAME", "\"${Config.Sentry.SENTRY_SPRING_JAKARTA_SDK_NAME}\"")
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
            "Sentry-SDK-Name" to Config.Sentry.SENTRY_SPRING_JAKARTA_SDK_NAME,
            "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-spring-jakarta",
            "Implementation-Vendor" to "Sentry",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}
