import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id(Config.BuildPlugins.springDependencyManagement) version Config.BuildPlugins.springDependencyManagementVersion
    kotlin("jvm")
    kotlin("plugin.spring") version Config.kotlinVersion
    id("war")
    id("org.gretty") version "3.0.3"
}

group = "io.sentry.sample.spring"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

dependencies {
    implementation("javax.servlet:javax.servlet-api:4.0.1")
    implementation("org.springframework.security:spring-security-web:5.4.2")
    implementation("org.springframework.security:spring-security-config:5.4.2")
    implementation("org.springframework:spring-webmvc:5.3.3")
    implementation(Config.Libs.logbackClassic)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    implementation(project(":sentry-spring"))
    implementation(project(":sentry-logback"))
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
