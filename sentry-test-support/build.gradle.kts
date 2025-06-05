plugins {
    `java-library`
    id("io.sentry.javadoc")
    kotlin("jvm")
    jacoco
    alias(libs.plugins.errorprone)
    alias(libs.plugins.gradle.versions)
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

dependencies {
    api(projects.sentry)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.nopen.annotations)

    errorprone(libs.errorprone.core)
    errorprone(libs.nopen.checker)

    // tests
    implementation(kotlin(Config.kotlinStdLib))
    implementation(libs.kotlin.test.junit)
    implementation(libs.mockito.kotlin)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}
