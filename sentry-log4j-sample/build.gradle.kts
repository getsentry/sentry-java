plugins {
    `java-library`
    kotlin("jvm")
}

dependencies {
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)
    implementation(Config.Libs.log4j)
    implementation(project(":sentry-log4j"))
    implementation(project(":sentry-core"))
    implementation(kotlin("stdlib-jdk8"))
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}
