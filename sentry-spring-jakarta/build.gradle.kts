import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
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
    id(Config.BuildPlugins.springBoot) version Config.springBoot3Version apply false
    id(Config.BuildPlugins.springDependencyManagement) version Config.BuildPlugins.springDependencyManagementVersion
}

repositories {
    mavenCentral()
}

the<DependencyManagementExtension>().apply {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
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
    compileOnly(Config.Libs.springWeb)
    compileOnly(Config.Libs.springAop)
    compileOnly(Config.Libs.springSecurityWeb)
    compileOnly(Config.Libs.aspectj)
    compileOnly(Config.Libs.servletApiJakarta)
    compileOnly(Config.Libs.slf4jApi)
    compileOnly(Config.Libs.contextPropagation)

    compileOnly(Config.Libs.springWebflux)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(projects.sentryTestSupport)
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.Libs.springBoot3StarterTest)
    testImplementation(Config.Libs.springBoot3StarterWeb)
    testImplementation(Config.Libs.springBoot3StarterWebflux)
    testImplementation(Config.Libs.springBoot3StarterSecurity)
    testImplementation(Config.Libs.springBoot3StarterAop)
    testImplementation(Config.Libs.contextPropagation)
    testImplementation(Config.TestLibs.awaitility)
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
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
    packageName("io.sentry.spring.jakarta")
    buildConfigField("String", "SENTRY_SPRING_JAKARTA_SDK_NAME", "\"${Config.Sentry.SENTRY_SPRING_JAKARTA_SDK_NAME}\"")
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
