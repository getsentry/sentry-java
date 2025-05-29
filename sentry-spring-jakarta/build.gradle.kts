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
    alias(libs.plugins.springboot3) apply false
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
    compileOnly(Config.Libs.aspectj)
    compileOnly(Config.Libs.servletApiJakarta)
    compileOnly(Config.Libs.slf4jApi)
    compileOnly(Config.Libs.contextPropagation)
    compileOnly(libs.otel)
    compileOnly(libs.springboot3.starter.graphql)
    compileOnly(libs.springboot3.starter.quartz)

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
    testImplementation(libs.springboot3.starter.aop)
    testImplementation(libs.springboot3.starter.graphql)
    testImplementation(libs.springboot3.starter.security)
    testImplementation(libs.springboot3.starter.test)
    testImplementation(libs.springboot3.starter.web)
    testImplementation(libs.springboot3.starter.webflux)
    testImplementation(Config.Libs.contextPropagation)
    testImplementation(Config.Libs.graphQlJavaNew)
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
