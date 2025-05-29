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
    kotlinOptions.languageVersion = Config.kotlinCompatibleLanguageVersion
}

dependencies {
    api(projects.sentry)
    api(projects.sentryGraphqlCore)
    compileOnly(Config.Libs.graphQlJava22)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.nopen.annotations)
    errorprone(libs.errorprone.core)
    errorprone(libs.nopen.checker)
    errorprone(libs.nullaway)

    // tests
    testImplementation(projects.sentry)
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.springboot.starter.graphql)
    testImplementation(Config.Libs.okhttp)
    testImplementation("com.netflix.graphql.dgs:graphql-error-types:4.9.2")
    testImplementation(Config.Libs.graphQlJava22)
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

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
    }
}

buildConfig {
    useJavaOutput()
    packageName("io.sentry.graphql22")
    buildConfigField("String", "SENTRY_GRAPHQL22_SDK_NAME", "\"${Config.Sentry.SENTRY_GRAPHQL22_SDK_NAME}\"")
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.jar {
    manifest {
        attributes(
            "Sentry-Version-Name" to project.version,
            "Sentry-SDK-Name" to Config.Sentry.SENTRY_GRAPHQL22_SDK_NAME,
            "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-graphql-22",
            "Implementation-Vendor" to "Sentry",
            "Implementation-Title" to project.name,
            "Implementation-Version" to project.version
        )
    }
}
