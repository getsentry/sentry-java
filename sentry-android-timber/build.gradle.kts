import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    kotlin("android")
    jacoco
    id(Config.QualityPlugins.gradleVersions)
    id(Config.QualityPlugins.detektPlugin)
}

android {
    compileSdk = Config.Android.compileSdkVersion

    defaultConfig {
        targetSdk = Config.Android.targetSdkVersion
        minSdk = Config.Android.minSdkVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
        buildConfigField("String", "SENTRY_TIMBER_SDK_NAME", "\"${Config.Sentry.SENTRY_TIMBER_SDK_NAME}\"")
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

kotlin {
    explicitApi()
}

tasks.withType<KotlinCompile>().configureEach {
    // Timber uses Kotlin 1.2
    kotlinOptions.languageVersion = "1.2"
}

dependencies {
    api(projects.sentry)

    api(Config.Libs.timber)

    implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))

    // tests
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.androidxJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
    testImplementation(Config.TestLibs.mockitoInline)
}

tasks.withType<Detekt> {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}

configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = true
}
