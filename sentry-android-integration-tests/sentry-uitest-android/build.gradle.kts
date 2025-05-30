import io.gitlab.arturbosch.detekt.Detekt
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("com.android.application")
    kotlin("android")
    alias(libs.plugins.errorprone)
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.detekt)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "io.sentry.uitest.android"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Runs each test in its own instance of Instrumentation. This way they are isolated from
        // one another and get their own Application instance.
        // https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner#enable-gradle
        // This doesn't work on some devices with Android 11+. Clearing package data resets permissions.
        // Check the readme for more info.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        buildConfigField("String", "ENVIRONMENT", "\"${System.getProperty("environment", "")}\"")
    }

    testOptions {
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    buildFeatures {
        // Determines whether to support View Binding.
        // Note that the viewBinding.enabled property is now deprecated.
        viewBinding = true
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = Config.androidComposeCompilerVersion
    }

    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    testBuildType = System.getProperty("testBuildType", "debug")

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // to be able to run release mode
            testProguardFiles("proguard-rules.pro")
        }
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
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
}

val applySentryIntegrations = System.getenv("APPLY_SENTRY_INTEGRATIONS")?.toBoolean() ?: true

dependencies {

    implementation(kotlin(Config.kotlinStdLib, org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION))

    if (applySentryIntegrations) {
        implementation(projects.sentryAndroid)
        implementation(projects.sentryCompose)
    } else {
        implementation(projects.sentryAndroidCore)
    }
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.test.espresso.idling.resource)
    implementation(libs.leakcanary)

    compileOnly(libs.nopen.annotations)

    errorprone(libs.errorprone.core)
    errorprone(libs.nopen.checker)
    errorprone(libs.nullaway)

    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestImplementation(projects.sentryTestSupport)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.awaitility3.kotlin)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.leakcanary.instrumentation)
    androidTestImplementation(libs.okhttp.mockwebserver)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
        option("NullAway:UnannotatedSubPackages", "io.sentry.uitest.android.databinding")
    }
}

tasks.withType<Detekt>().configureEach {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
