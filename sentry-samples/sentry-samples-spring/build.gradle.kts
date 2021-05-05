import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id(Config.BuildPlugins.springBoot) version Config.springBootVersion apply false
    id(Config.BuildPlugins.springDependencyManagement) version Config.BuildPlugins.springDependencyManagementVersion
    kotlin("jvm")
    kotlin("plugin.spring") version Config.kotlinVersion
    id("war")
    // https://github.com/gretty-gradle-plugin/gretty/issues/206
    id(Config.BuildPlugins.gretty) version Config.BuildPlugins.grettyVersion apply false
}

group = "io.sentry.sample.spring"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    implementation(Config.Libs.servletApi)
    implementation(Config.Libs.springWeb)
    implementation(Config.Libs.springAop)
    implementation(Config.Libs.aspectj)
    implementation(Config.Libs.springSecurityWeb)
    implementation(Config.Libs.springSecurityConfig)
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
