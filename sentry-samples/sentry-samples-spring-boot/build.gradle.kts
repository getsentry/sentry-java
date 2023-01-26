import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id(Config.BuildPlugins.springBoot) version Config.springBootVersion
    id(Config.BuildPlugins.springDependencyManagement) version Config.BuildPlugins.springDependencyManagementVersion
    kotlin("jvm")
    kotlin("plugin.spring") version Config.kotlinVersion
}

group = "io.sentry.sample.spring-boot"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

dependencies {
    implementation(Config.Libs.springBootStarterSecurity) {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
    implementation(Config.Libs.springBootStarterWeb) {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
    implementation(Config.Libs.springBootStarterWebflux) {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
    implementation(Config.Libs.springBootStarterAop) {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
    implementation(Config.Libs.aspectj)
    implementation(Config.Libs.springBootStarter) {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
    implementation(Config.Libs.kotlinReflect)
    implementation(Config.Libs.springBootStarterJdbc) {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    implementation(projects.sentrySpringBootStarter) {
        exclude("org.springframework.boot", "spring-boot-starter-logging")
    }
    implementation(projects.sentryLog4j2)
    implementation("org.springframework.boot:spring-boot-starter-log4j2")

    // database query tracing
    implementation(projects.sentryJdbc)
    runtimeOnly(Config.TestLibs.hsqldb)
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
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}
