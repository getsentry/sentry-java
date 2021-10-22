import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
}

android {
    compileSdk = Config.Android.compileSdkVersion

    defaultConfig {
        targetSdk = Config.Android.targetSdkVersion
        minSdk = Config.Android.minSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
        isWarningsAsErrors = true
        isCheckDependencies = true

        // We run a full lint analysis as build part in CI, so skip vital checks for assemble tasks.
        isCheckReleaseBuilds = false
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

tasks.withType<Test> {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = false
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
}
