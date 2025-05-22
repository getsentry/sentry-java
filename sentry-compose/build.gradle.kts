
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    alias(libs.plugins.kover)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.dokka.javadoc)
    `maven-publish` // necessary for publishMavenLocal task to publish correct artifacts
}

kotlin {
    explicitApi()

    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }

    sourceSets.all {
        // Allow all experimental APIs, since MPP projects are themselves experimental
        languageSettings.apply {
            optIn("kotlin.Experimental")
            optIn("kotlin.ExperimentalMultiplatform")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                compileOnly(compose.runtime)
                compileOnly(compose.ui)
            }
        }
        val androidMain by getting {
            dependencies {
                api(projects.sentry)
                api(projects.sentryAndroidNavigation)

                compileOnly(libs.androidx.navigation.compose)
                implementation(Config.Libs.lifecycleCommonJava8)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(libs.kotlin.test.junit)
                implementation(libs.mockito.kotlin)
                implementation(libs.mockito.inline)
                implementation(libs.androidx.navigation.compose)
                implementation(libs.roboelectric)
                implementation(libs.androidx.test.rules)
                implementation(libs.androidx.test.runner)
                implementation(libs.androidx.test.ext.junit)
                implementation(Config.TestLibs.composeUiTestJunit4)
            }
        }
    }
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.compose"

    defaultConfig {
        minSdk = Config.Android.minSdkVersion

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }

    buildTypes {
        getByName("debug") {
            consumerProguardFiles("proguard-rules.pro")
        }
        getByName("release") {
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    testOptions {
        animationsDisabled = true
        unitTests.apply {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
        }
    }

    lint {
        warningsAsErrors = true
        checkDependencies = true

        // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
        checkReleaseBuilds = false
    }

    buildFeatures {
        buildConfig = true
    }

    androidComponents.beforeVariants {
        it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    }
}

tasks.withType<Detekt>().configureEach {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<DokkaTask>().configureEach {
    // suppress unattached source sets for docs
    dokkaSourceSets {
        matching {
            it.name.contains("androidandroid", ignoreCase = true) ||
                it.name.contains("testfixtures", ignoreCase = true)
        }.configureEach {
            suppress.set(true)
        }
    }
}
