import io.gitlab.arturbosch.detekt.Detekt

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    jacoco
    id(Config.QualityPlugins.gradleVersions)
    id(Config.QualityPlugins.detektPlugin)
    `maven-publish` // necessary for publishMavenLocal task to publish correct artifacts
}

kotlin {
    explicitApi()

    android {
        publishLibraryVariants("release")
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
                api(compose.runtime)
                api(compose.ui)

                implementation(Config.Libs.kotlinStdLib)
            }
        }
        val androidMain by getting {
            dependencies {
                api(projects.sentry)
                api(projects.sentryAndroidNavigation)

                api(Config.Libs.composeNavigation)
                implementation(Config.Libs.lifecycleCommonJava8)
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(Config.TestLibs.kotlinTestJunit)
                implementation(Config.TestLibs.mockitoKotlin)
                implementation(Config.TestLibs.mockitoInline)
            }
        }
    }
}

android {
    compileSdk = Config.Android.compileSdkVersion

    defaultConfig {
        targetSdk = Config.Android.targetSdkVersion
        minSdk = Config.Android.minSdkVersionCompose

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }

    buildTypes {
        getByName("debug")
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

    variantFilter {
        if (Config.Android.shouldSkipDebugVariant(buildType.name)) {
            ignore = true
        }
    }
}

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = false
    }
}

tasks.withType<Detekt> {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
