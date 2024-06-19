import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.QualityPlugins.jacocoAndroid)
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.android.core"

    defaultConfig {
        targetSdk = Config.Android.targetSdkVersion
        minSdk = Config.Android.minSdkVersion

        testInstrumentationRunner = Config.TestLibs.androidJUnitRunner

        buildConfigField("String", "SENTRY_ANDROID_SDK_NAME", "\"${Config.Sentry.SENTRY_ANDROID_SDK_NAME}\"")

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    buildTypes {
        getByName("debug")
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

    // needed because of Kotlin 1.4.x
    configurations.all {
        resolutionStrategy.force(Config.CompileOnly.jetbrainsAnnotations)
    }

    variantFilter {
        if (Config.Android.shouldSkipDebugVariant(buildType.name)) {
            ignore = true
        }
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
    compileOnly(projects.sentryComposeHelper)

    // lifecycle processor, session tracking
    implementation(Config.Libs.lifecycleProcess)
    implementation(Config.Libs.lifecycleCommonJava8)
    implementation(Config.Libs.androidxCore)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorprone(Config.CompileOnly.errorProneNullAway)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
    testImplementation(Config.TestLibs.robolectric)
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.androidxCore)
    testImplementation(Config.TestLibs.androidxRunner)
    testImplementation(Config.TestLibs.androidxJunit)
    testImplementation(Config.TestLibs.androidxCoreKtx)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.TestLibs.mockitoInline)
    testImplementation(Config.TestLibs.awaitility)
    testImplementation(projects.sentryTestSupport)
    testImplementation(projects.sentryAndroidFragment)
    testImplementation(projects.sentryAndroidTimber)
    testImplementation(projects.sentryAndroidReplay)
    testImplementation(projects.sentryComposeHelper)
    testImplementation(projects.sentryAndroidNdk)
    testRuntimeOnly(Config.Libs.composeUi)
    testRuntimeOnly(Config.Libs.timber)
    testRuntimeOnly(Config.Libs.fragment)
}
