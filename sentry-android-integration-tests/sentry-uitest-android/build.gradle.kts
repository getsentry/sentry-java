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
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.uitest.android"

    defaultConfig {
        minSdk = Config.Android.minSdkVersion
        targetSdk = Config.Android.targetSdkVersion
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = Config.TestLibs.androidJUnitRunner
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
    implementation(Config.Libs.appCompat)
    implementation(Config.Libs.androidxCore)
    implementation(Config.Libs.composeActivity)
    implementation(Config.Libs.composeFoundation)
    implementation(Config.Libs.composeMaterial)
    implementation(Config.Libs.androidxRecylerView)
    implementation(Config.Libs.constraintLayout)
    implementation(Config.TestLibs.espressoIdlingResource)
    implementation(Config.Libs.leakCanary)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)

    androidTestImplementation(projects.sentryTestSupport)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(Config.TestLibs.espressoCore)
    androidTestImplementation(Config.TestLibs.androidxRunner)
    androidTestImplementation(Config.TestLibs.androidxTestRules)
    androidTestImplementation(Config.TestLibs.androidxTestCoreKtx)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(Config.TestLibs.androidxJunit)
    androidTestImplementation(Config.TestLibs.leakCanaryInstrumentation)
    androidTestImplementation(Config.TestLibs.awaitility3)
    androidTestUtil(Config.TestLibs.androidxTestOrchestrator)
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
