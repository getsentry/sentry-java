import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.springboot2) apply false
    alias(libs.plugins.spring.dependency.management)
    kotlin("jvm")
    alias(libs.plugins.kotlin.spring)
    id("war")
    alias(libs.plugins.gretty)
}

group = "io.sentry.sample.spring"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
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
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}
