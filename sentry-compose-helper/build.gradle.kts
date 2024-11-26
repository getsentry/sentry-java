import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    `java-library`
    id(Config.QualityPlugins.gradleVersions)
    id(Config.BuildPlugins.buildConfig) version Config.BuildPlugins.buildConfigVersion
}

kotlin {
    jvm {
        withJava()
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(projects.sentry)

                compileOnly(compose.runtime)
                compileOnly(compose.ui)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.ui)

                implementation(Config.TestLibs.kotlinTestJunit)
                implementation(Config.TestLibs.mockitoKotlin)
                implementation(Config.TestLibs.mockitoInline)
            }
        }
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

val embeddedJar by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("embeddedJar", File("$buildDir/libs/sentry-compose-helper-jvm-$version.jar"))
}

tasks.jar {
    manifest {
        attributes("Automatic-Module-Name" to "io.sentry.compose.helper")
    }
}

buildConfig {
    sourceSets.getByName("jvmMain") {
        useKotlinOutput()
        className("BuildConfig")
        packageName("io.sentry.compose.helper")
        buildConfigField("String", "SENTRY_COMPOSE_HELPER_SDK_NAME", "\"${Config.Sentry.SENTRY_COMPOSE_HELPER_SDK_NAME}\"")
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }
}
