
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
    alias(libs.plugins.springboot2) apply false
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    kotlinOptions.languageVersion = Config.kotlinCompatibleLanguageVersion
}

dependencies {
    api(projects.sentry)

    compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))
    compileOnly(Config.Libs.springWeb)
    compileOnly(Config.Libs.springAop)
    compileOnly(Config.Libs.springSecurityWeb)
    compileOnly(Config.Libs.aspectj)
    compileOnly(Config.Libs.servletApi)
    compileOnly(Config.Libs.slf4jApi)
    compileOnly(Config.Libs.springWebflux)
    compileOnly(projects.sentryGraphql)
    compileOnly(projects.sentryQuartz)
    compileOnly(libs.otel)
    compileOnly(libs.springboot.starter.graphql)
    compileOnly(libs.springboot.starter.quartz)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(projects.sentryTestSupport)
    testImplementation(projects.sentryGraphql)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(libs.awaitility.kotlin)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.springboot.starter.aop)
    testImplementation(libs.springboot.starter.graphql)
    testImplementation(libs.springboot.starter.security)
    testImplementation(libs.springboot.starter.test)
    testImplementation(libs.springboot.starter.web)
    testImplementation(libs.springboot.starter.webflux)
    testImplementation(Config.Libs.graphQlJava)
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
    packageName("io.sentry.spring")
    buildConfigField("String", "SENTRY_SPRING_SDK_NAME", "\"${Config.Sentry.SENTRY_SPRING_SDK_NAME}\"")
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
            "Sentry-SDK-Name" to Config.Sentry.SENTRY_SPRING_SDK_NAME,
            "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-spring",
            "Implementation-Vendor" to "Sentry",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}
