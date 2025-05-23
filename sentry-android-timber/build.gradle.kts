import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    alias(libs.plugins.jacoco.android)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.detekt)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "io.sentry.android.timber"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
        buildConfigField("String", "SENTRY_TIMBER_SDK_NAME", "\"${Config.Sentry.SENTRY_TIMBER_SDK_NAME}\"")
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
        kotlinOptions.languageVersion = Config.kotlinCompatibleLanguageVersion
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

    compileOnly(Config.Libs.timber)

    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))

    // tests
    testImplementation(Config.Libs.timber)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)
}

tasks.withType<Detekt>().configureEach {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
