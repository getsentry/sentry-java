import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.QualityPlugins.jacocoAndroid)
    id(Config.QualityPlugins.gradleVersions)
    id(Config.QualityPlugins.detektPlugin)
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.android.fragment"

    defaultConfig {
        minSdk = Config.Android.minSdkVersion

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    buildTypes {
        getByName("debug") {
            consumerProguardFiles("proguard-rules.pro")
        }
        getByName("release") {
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
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

kotlin {
    explicitApi()
}

dependencies {
    api(projects.sentry)

    compileOnly(Config.Libs.fragment)

    // tests
    testImplementation(Config.Libs.fragment)
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.TestLibs.mockitoInline)
}

tasks.withType<Detekt>().configureEach {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
