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
    implementation(Config.Libs.springBootStarterSecurity)
    implementation(Config.Libs.springBootStarterWeb)
    implementation(Config.Libs.springBootStarterWebflux)
    implementation(Config.Libs.springBootStarterAop)
    implementation(Config.Libs.aspectj)
    implementation(Config.Libs.springBootStarter)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    implementation(projects.sentrySpringBootStarter)
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
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
}
