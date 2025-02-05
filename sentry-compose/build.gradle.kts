import com.android.build.gradle.internal.tasks.LibraryAarJarsTask
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id(Config.QualityPlugins.kover)
    id(Config.QualityPlugins.gradleVersions)
    id(Config.QualityPlugins.detektPlugin)
    id(Config.BuildPlugins.dokkaPluginAlias)
    `maven-publish` // necessary for publishMavenLocal task to publish correct artifacts
}

kotlin {
    explicitApi()

    android {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }
    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }

    sourceSets.all {
        // Allow all experimental APIs, since MPP projects are themselves experimental
        languageSettings.apply {
            optIn("kotlin.Experimental")
            optIn("kotlin.ExperimentalMultiplatform")
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                compileOnly(compose.runtime)
                compileOnly(compose.ui)

                compileOnly(projects.sentryComposeHelper)
            }
        }
        val androidMain by getting {
            dependencies {
                api(projects.sentry)
                api(projects.sentryAndroidNavigation)

                compileOnly(Config.Libs.composeNavigation)
                implementation(Config.Libs.lifecycleCommonJava8)
            }
        }
        val androidUnitTest by getting {
            dependencies {
                implementation(Config.TestLibs.kotlinTestJunit)
                implementation(Config.TestLibs.mockitoKotlin)
                implementation(Config.TestLibs.mockitoInline)
                implementation(Config.Libs.composeNavigation)
            }
        }
    }
}

android {
    compileSdk = Config.Android.compileSdkVersion
    namespace = "io.sentry.compose"

    defaultConfig {
        minSdk = Config.Android.minSdkVersion

        // for AGP 4.1
        buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
    }

    sourceSets["main"].apply {
        manifest.srcFile("src/androidMain/AndroidManifest.xml")
    }

    buildTypes {
        getByName("debug")
        getByName("release") {
            consumerProguardFiles("proguard-rules.pro")
        }
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

tasks.withType<Detekt> {
    // Target version of the generated JVM bytecode. It is used for type resolution.
    jvmTarget = JavaVersion.VERSION_1_8.toString()
}

tasks.withType<DokkaTask>().configureEach {
    // suppress unattached source sets for docs
    dokkaSourceSets {
        matching {
            it.name.contains("androidandroid", ignoreCase = true) ||
                it.name.contains("testfixtures", ignoreCase = true)
        }.configureEach {
            suppress.set(true)
        }
    }
}

/**
 * Due to https://youtrack.jetbrains.com/issue/KT-30878
 * you can not have java sources in a KMP-enabled project which has the android-lib plugin applied.
 * Thus we compile relevant java code in sentry-compose-helper first and embed it in here.
 */
val embedComposeHelperConfig by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    embedComposeHelperConfig(
        project(":" + projects.sentryComposeHelper.name, "embeddedJar")
    )
}

tasks.withType<LibraryAarJarsTask> {
    mainScopeClassFiles.setFrom(embedComposeHelperConfig)
}
