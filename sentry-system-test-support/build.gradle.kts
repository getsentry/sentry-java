plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    alias(libs.plugins.errorprone)
    alias(libs.plugins.gradle.versions)
    id("com.apollographql.apollo3") version "3.8.2"
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
}

dependencies {
    api(projects.sentry)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.nopen.annotations)
    compileOnly(libs.springboot3.starter.test) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    compileOnly(libs.springboot3.starter.web)
    api(Config.Libs.apolloKotlin)
    implementation(Config.Libs.jacksonKotlin)
    implementation(Config.Libs.jacksonDatabind)
    api(projects.sentryTestSupport)
    implementation(Config.Libs.okhttp)

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

apollo {
    service("service") {
        srcDir("src/main/graphql")
        packageName.set("io.sentry.samples.graphql")
        outputDirConnection {
            connectToKotlinSourceSet("main")
        }
    }
}
