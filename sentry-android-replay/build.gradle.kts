import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.QualityPlugins.jacocoAndroid)
    id(Config.QualityPlugins.gradleVersions)
    // TODO: enable it later
//    id(Config.QualityPlugins.detektPlugin)
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.android.replay"

    defaultConfig {
        minSdk = Config.Android.minSdkVersionReplay

        testInstrumentationRunner = Config.TestLibs.androidJUnitRunner

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        getByName("debug")
        getByName("release")
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

    androidComponents.beforeVariants {
        it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    }

    configurations.all {
        resolutionStrategy.force(Config.CompileOnly.jetbrainsAnnotations)
    }
}

kotlin {
    explicitApi()
}

dependencies {
    api(projects.sentry)

    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))

    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(projects.sentryTestSupport)
    testImplementation(Config.TestLibs.robolectric)
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.androidxRunner)
    testImplementation(Config.TestLibs.androidxJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.TestLibs.mockitoInline)
    testImplementation(Config.TestLibs.awaitility)
}

tasks.withType<Detekt> {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
