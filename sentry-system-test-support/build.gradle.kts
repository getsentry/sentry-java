plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
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
    compileOnly(Config.Libs.springBoot3StarterTest) {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    compileOnly(Config.Libs.springBoot3StarterWeb)
    api(Config.Libs.apolloKotlin)
    implementation(Config.Libs.jacksonKotlin)
    api(projects.sentryTestSupport)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    implementation(kotlin(Config.kotlinStdLib))
    implementation(Config.TestLibs.kotlinTestJunit)
    implementation(Config.TestLibs.mockitoKotlin)
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
