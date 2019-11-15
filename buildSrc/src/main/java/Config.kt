object Config {
    val kotlinVersion = "1.3.50"
    val kotlinStdLib = "stdlib-jdk8"

    object BuildPlugins {
        val androidGradle = "com.android.tools.build:gradle:3.5.2"
        val kotlinGradlePlugin = "gradle-plugin"
    }

    object Android {
        private val sdkVersion = 29

        val buildToolsVersion = "29.0.2"
        val minSdkVersion = 14
        val minSdkVersionNdk = 21
        val targetSdkVersion = sdkVersion
        val compileSdkVersion = sdkVersion
    }

    object Libs {
        val appCompat = "androidx.appcompat:appcompat:1.1.0"
        val timber = "com.jakewharton.timber:timber:4.7.1"
        val gson = "com.google.code.gson:gson:2.8.6"
        val leakCanary = "com.squareup.leakcanary:leakcanary-android:2.0-beta-3"
    }

    object TestLibs {
        private val androidxTestVersion = "1.2.0"

        val kotlinTestJunit = "org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion"
        val androidxCore = "androidx.test:core:$androidxTestVersion"
        val androidxRunner = "androidx.test:runner:$androidxTestVersion"
        val androidxJunit = "androidx.test.ext:junit:1.1.1"
        val robolectric = "org.robolectric:robolectric:4.3.1"
        val junit = "junit:junit:4.12"
        val espressoCore = "androidx.test.espresso:espresso-core:3.2.0"
        val androidxOrchestrator = "androidx.test:orchestrator:$androidxTestVersion"
        val mockitoKotlin = "com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0"
    }

    object QualityPlugins {
        val jacocoVersion = "0.8.5"
        val spotless = "com.diffplug.gradle.spotless"
        val spotlessVersion = "3.25.0"
        val errorpronePlugin = "net.ltgt.gradle:gradle-errorprone-plugin:1.1.1"
    }

    object Sentry {
        val SENTRY_CLIENT_NAME = "sentry.java.android"
        val group = "io.sentry"
//        TODO: change version to publish new version
        val version = "2.0.0-alpha04"
        val description = "SDK for sentry.io"
//        TODO: change version code to publish new version, follow the pattern of `version`
        val buildVersionCode = 20004
        val website = "https://sentry.io"
        val userOrg = "getsentry"
        val repoName = "sentry-android"
        val licence = "MIT"
        val issueTracker = "https://github.com/getsentry/sentry-android/issues"
        val repository = "https://github.com/getsentry/sentry-android"
        val devName = "Sentry Team and Contributors"
        val devEmail = "accounts@sentry.io"
        val devUser = "getsentry"
    }

    object CompileOnly {
        private val nopenVersion = "1.0.1"

        val jetbrainsAnnotations = "org.jetbrains:annotations:18.0.0"
        val nopen = "com.jakewharton.nopen:nopen-annotations:$nopenVersion"
        val nopenChecker = "com.jakewharton.nopen:nopen-checker:$nopenVersion"
        val errorprone = "com.google.errorprone:error_prone_core:2.3.3"
        val errorProneJavac8 = "com.google.errorprone:javac:9+181-r4173-1"
    }

    object Deploy {
//        TODO: change version to 0.9.2 to publish new version, which supports `sign` field
//        lets wait a bit for PR to be reviewed or we publish ourselves https://github.com/novoda/bintray-release/issues/301
        val novodaBintray = "com.novoda:bintray-release:0.9.1"
        val novodaBintrayId = "com.novoda.bintray-release"
        val dryRun = true // If set to true this will run everything but it won't upload the package to bintray. If false then it will upload normally.
        val override = true
        val sign = true
    }
}
