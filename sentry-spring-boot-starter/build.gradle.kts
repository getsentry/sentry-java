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
    id(Config.BuildPlugins.springBoot) version Config.springBootVersion apply false
}

apply(plugin = Config.BuildPlugins.springDependencyManagement)

the<DependencyManagementExtension>().apply {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
        mavenBom(Config.Libs.okhttpBom)
    }
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    kotlinOptions.languageVersion = Config.springKotlinCompatibleLanguageVersion
}

dependencies {
    api(project(":sentry"))
    api(project(":sentry-spring"))
    compileOnly(project(":sentry-logback"))
    compileOnly(project(":sentry-apache-http-client-5"))
    implementation(Config.Libs.springBootStarter)
    compileOnly(Config.Libs.springWeb)
    compileOnly(Config.Libs.springWebflux)
    compileOnly(Config.Libs.servletApi)
    compileOnly(Config.Libs.springBootStarterAop)
    compileOnly(Config.Libs.springBootStarterSecurity)
    compileOnly(Config.Libs.reactorCore)

    annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
    annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorproneJavac(Config.CompileOnly.errorProneJavac8)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(project(":sentry-logback"))
    testImplementation(project(":sentry-apache-http-client-5"))
    testImplementation(project(":sentry-test-support"))
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.TestLibs.mockWebserver)
    testImplementation(Config.Libs.okhttp)
    testImplementation(Config.Libs.springBootStarterTest)
    testImplementation(Config.Libs.springBootStarterWeb)
    testImplementation(Config.Libs.springBootStarterWebflux)
    testImplementation(Config.Libs.springBootStarterSecurity)
    testImplementation(Config.Libs.springBootStarterAop)
    testImplementation(Config.TestLibs.awaitility)
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
        xml.isEnabled = true
        html.isEnabled = false
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
