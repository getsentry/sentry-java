import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES

plugins {
    alias(libs.plugins.springboot2) apply false
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    id("war")
    alias(libs.plugins.gretty)
}

group = "io.sentry.sample.spring"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

spotless {
    kotlinGradle {
        // This file throws an unclear error
        targetExclude("build.gradle.kts")
    }
}

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(BOM_COORDINATES)
    }
}

dependencies {
    implementation(Config.Libs.springWeb)
    implementation(Config.Libs.springAop)
    implementation(Config.Libs.aspectj)
    implementation(Config.Libs.springSecurityWeb)
    implementation(Config.Libs.springSecurityConfig)
    implementation(Config.Libs.kotlinReflect)
    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    implementation(projects.sentrySpring)
    implementation(projects.sentryLogback)
    implementation(libs.logback.classic)
    implementation(libs.servlet.api)
    testImplementation(libs.springboot.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile>().configureEach {
    kotlin {
        compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
        compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
    }
}
