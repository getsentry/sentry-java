import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.springboot2)
    alias(libs.plugins.spring.dependency.management)
    kotlin("jvm")
    alias(libs.plugins.kotlin.spring)
}

group = "io.sentry.sample.spring-boot"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_1_8
java.targetCompatibility = JavaVersion.VERSION_1_8

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.springboot.starter.web)
    implementation(Config.Libs.kotlinReflect)
    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    implementation(projects.sentrySpringBootStarter)
    implementation(projects.sentryGraphql)
    implementation(platform("com.netflix.graphql.dgs:graphql-dgs-platform-dependencies:4.9.2"))
    implementation("com.netflix.graphql.dgs:graphql-dgs-subscriptions-websockets-autoconfigure:4.9.2")
    implementation("com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter")
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
