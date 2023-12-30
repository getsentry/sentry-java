import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.configurationcache.extensions.capitalized
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

        buildConfigField(
            "String",
            "SENTRY_ANDROID_SDK_NAME",
            "\"${Config.Sentry.SENTRY_ANDROID_SDK_NAME}\""
        )

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

tasks {
    withType<JavaCompile>().configureEach {
        options.errorprone {
            check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "io.sentry")
        }
    }

    withType<Test>().configureEach {
        testLogging.showStandardStreams = true
        testLogging.exceptionFormat = TestExceptionFormat.FULL
        testLogging.events = setOf(
            TestLogEvent.SKIPPED,
            TestLogEvent.PASSED,
            TestLogEvent.FAILED
        )
        maxParallelForks = Runtime.getRuntime().availableProcessors() / 2

        // Cap JVM args per test
        minHeapSize = "128m"
        maxHeapSize = "1g"
        if (!this.name.contains("robolectric", ignoreCase = true)) {
            filter {
                exclude { element ->
                    if (element.isDirectory || !element.file.exists()) {
                        return@exclude false
                    }
                    return@exclude element.file
                        .readText()
                        .contains("Landroidx/test/ext/junit/runners/AndroidJUnit4;")
                }
            }
        }
        dependsOn("cleanTest")
    }
}

afterEvaluate {
    setOf("debug", "release").forEach { variant ->
        task<Test>("${variant}RobolectricTest") {
            group = "verification"
            description = "Runs the Robolectric tests"

            val testTask = tasks.findByName("test${variant.capitalized()}UnitTest") as Test
            classpath = testTask.classpath
            testClassesDirs = testTask.testClassesDirs
            filter {
                include { element ->
                    if (element.isDirectory || !element.file.exists()) {
                        return@include true
                    }
                    return@include element.file
                        .readText()
                        .contains("Landroidx/test/ext/junit/runners/AndroidJUnit4;")
                }
            }
        }
    }
}

dependencies {
    api(projects.sentry)
    compileOnly(projects.sentryAndroidFragment)
    compileOnly(projects.sentryAndroidTimber)
    compileOnly(projects.sentryCompose)

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
    testImplementation(projects.sentryComposeHelper)
    testImplementation(projects.sentryAndroidNdk)
    testRuntimeOnly(Config.Libs.composeUi)
    testRuntimeOnly(Config.Libs.timber)
    testRuntimeOnly(Config.Libs.fragment)
}
