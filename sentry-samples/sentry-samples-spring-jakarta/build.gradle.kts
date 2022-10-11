import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    id(Config.BuildPlugins.springBoot) version Config.springBoot3Version apply false
    id(Config.BuildPlugins.springDependencyManagement) version Config.BuildPlugins.springDependencyManagementVersion
    kotlin("jvm")
    kotlin("plugin.spring") version Config.kotlinVersion
    id("war")
    id(Config.BuildPlugins.gretty) version Config.BuildPlugins.grettyVersion
}

group = "io.sentry.sample.spring-jakarta"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.spring.io/plugins-snapshot")
    }
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

val jakartaTransform by configurations.creating

dependencies {
    jakartaTransform("org.eclipse.transformer:org.eclipse.transformer:0.5.0")
    jakartaTransform("org.eclipse.transformer:org.eclipse.transformer.cli:0.5.0")
    jakartaTransform("org.eclipse.transformer:org.eclipse.transformer.jakarta:0.5.0")

    implementation(Config.Libs.servletApi)
    implementation(Config.Libs.springWeb)
    implementation(Config.Libs.springAop)
    implementation(Config.Libs.aspectj)
    implementation(Config.Libs.springSecurityWeb)
    implementation(Config.Libs.springSecurityConfig)
    implementation(Config.Libs.logbackClassic)
    implementation(Config.Libs.kotlinReflect)
    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    implementation(projects.sentrySpringJakarta)
    implementation(projects.sentryLogback)
    testImplementation(Config.Libs.springBootStarterTest) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

task("jakartaTransformation", JavaExec::class) {
    main = "org.eclipse.transformer.cli.JakartaTransformerCLI"
    classpath = configurations.getByName("jakartaTransform") //sourceSets["main"].compileClasspath
    args = listOf("../sentry-samples-spring/src", "src", "-o")
}
