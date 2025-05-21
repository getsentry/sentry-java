import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    alias(libs.plugins.jacoco.android)
    alias(libs.plugins.errorprone)
    alias(libs.plugins.gradle.versions)
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.android.core"

    defaultConfig {
        minSdk = Config.Android.minSdkVersion

        testInstrumentationRunner = Config.TestLibs.androidJUnitRunner

        buildConfigField("String", "SENTRY_ANDROID_SDK_NAME", "\"${Config.Sentry.SENTRY_ANDROID_SDK_NAME}\"")

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

    // needed because of Kotlin 1.4.x
    configurations.all {
        resolutionStrategy.force(Config.CompileOnly.jetbrainsAnnotations)
    }

    androidComponents.beforeVariants {
        it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
    }
}

dependencies {
    api(projects.sentry)
    compileOnly(projects.sentryAndroidFragment)
    compileOnly(projects.sentryAndroidTimber)
    compileOnly(projects.sentryAndroidReplay)
    compileOnly(projects.sentryCompose)

    // lifecycle processor, session tracking
    implementation(Config.Libs.lifecycleProcess)
    implementation(Config.Libs.lifecycleCommonJava8)
    implementation(libs.androidx.core)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    testImplementation(libs.roboelectric)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.androidx.core.ktx)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.mockito.inline)
    testImplementation(Config.TestLibs.awaitility)
    testImplementation(projects.sentryTestSupport)
    testImplementation(projects.sentryAndroidFragment)
    testImplementation(projects.sentryAndroidTimber)
    testImplementation(projects.sentryAndroidReplay)
    testImplementation(projects.sentryCompose)
    testImplementation(projects.sentryAndroidNdk)
    testRuntimeOnly(Config.Libs.composeUi)
    testRuntimeOnly(Config.Libs.timber)
    testRuntimeOnly(Config.Libs.fragment)
}
