import io.gitlab.arturbosch.detekt.Detekt
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("com.android.library")
    kotlin("android")
    id(Config.BuildPlugins.androidxBenchmark)
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
    id(Config.QualityPlugins.detektPlugin)
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.uitest.android.microbenchmark"

    defaultConfig {
        minSdk = Config.Android.minSdkVersion

        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,LOW-BATTERY"
    }

    // Needs to be 34+, as otherwise test will crash as background activity launches are not supported
    // See https://github.com/dayanruben/android-test/commit/a86bf09de9c6af89998baecc88677a9d6b8ff881
    @Suppress("UnstableApiUsage")
    testOptions {
        targetSdk = 35
    }

    testBuildType = "release"

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

dependencies {
    implementation(kotlin(Config.kotlinStdLib, org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION))
    implementation(projects.sentryAndroid)

    androidTestImplementation(Config.TestLibs.androidxMicroBenchmark)
    androidTestImplementation(projects.sentryTestSupport)
    androidTestImplementation(Config.TestLibs.kotlinTestJunit)
    androidTestImplementation(Config.TestLibs.androidxTestCoreKtx)
    androidTestImplementation(Config.TestLibs.androidxRunner)
    androidTestImplementation(Config.TestLibs.androidxTestRules)
    androidTestImplementation(Config.TestLibs.androidxJunit)
}

tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "io.sentry")
        option("NullAway:UnannotatedSubPackages", "io.sentry.uitest.android.benchmark.databinding")
    }
}

tasks.withType<Detekt> {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}
