object Config {
    val kotlinVersion = "1.4.0"
    val kotlinStdLib = "stdlib-jdk8"

    object BuildPlugins {
        val androidGradle = "com.android.tools.build:gradle:4.0.1"
        val kotlinGradlePlugin = "gradle-plugin"
        val buildConfig = "com.github.gmazzo.buildconfig"
        val buildConfigVersion = "2.0.2"
    }

    object Android {
        private val sdkVersion = 29

        val buildToolsVersion = "29.0.3"
        val minSdkVersion = 14
        val minSdkVersionNdk = 16
        val targetSdkVersion = sdkVersion
        val compileSdkVersion = sdkVersion
        val cmakeVersion = "3.10.2"
        val ndkVersion = "21.0.6113669" // 21.1.6352462 when https://discuss.lgtm.com/t/android-ndk-v21-1-6352462-is-not-available/2910 is fixed
        val abiFilters = listOf("x86", "armeabi-v7a", "x86_64", "arm64-v8a")
    }

    object Libs {
        val appCompat = "androidx.appcompat:appcompat:1.2.0"
        val timber = "com.jakewharton.timber:timber:4.7.1"
        // only bump gson if https://github.com/google/gson/issues/1597 is fixed
        val gson = "com.google.code.gson:gson:2.8.5"
        val leakCanary = "com.squareup.leakcanary:leakcanary-android:2.4"

        private val lifecycleVersion = "2.2.0"
        val lifecycleProcess = "androidx.lifecycle:lifecycle-process:$lifecycleVersion"
        val lifecycleCommonJava8 = "androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion"
    }

    object TestLibs {
        private val androidxTestVersion = "1.3.0"

        val kotlinTestJunit = "org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion"
        val androidxCore = "androidx.test:core:$androidxTestVersion"
        val androidxRunner = "androidx.test:runner:$androidxTestVersion"
        val androidxJunit = "androidx.test.ext:junit:1.1.2"
        val robolectric = "org.robolectric:robolectric:4.4"
        val mockitoKotlin = "com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0"
        val mockitoInline = "org.mockito:mockito-inline:3.5.10"
        val awaitility = "org.awaitility:awaitility-kotlin:4.0.3"
    }

    object QualityPlugins {
        val jacocoVersion = "0.8.5"
        val spotless = "com.diffplug.spotless"
        val spotlessVersion = "5.3.0"
        val errorProne = "net.ltgt.errorprone"
        val errorpronePlugin = "net.ltgt.gradle:gradle-errorprone-plugin:1.2.1"
        val gradleVersionsPlugin = "com.github.ben-manes:gradle-versions-plugin:0.31.0"
        val gradleVersions = "com.github.ben-manes.versions"
        val detekt = "io.gitlab.arturbosch.detekt"
        val detektVersion = "1.12.0"
        val detektPlugin = "io.gitlab.arturbosch.detekt"
    }

    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
        val SENTRY_ANDROID_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.android"
        val group = "io.sentry"
        val description = "SDK for sentry.io"
        val website = "https://sentry.io"
        val userOrg = "getsentry"
        val repoName = "sentry-android"
        val licence = "MIT"
        val licenceUrl = "http://www.opensource.org/licenses/mit-license.php"
        val issueTracker = "https://github.com/getsentry/sentry-android/issues"
        val repository = "https://github.com/getsentry/sentry-android"
        val devName = "Sentry Team and Contributors"
        val devEmail = "accounts@sentry.io"
        val scmConnection = "scm:git:git://github.com/getsentry/sentry-android.git"
        val scmDevConnection = "scm:git:ssh://github.com:getsentry/sentry-android.git"
        val scmUrl = "https://github.com/getsentry/sentry-android/tree/master"
        val versionNameProp = "versionName"
        val buildVersionCodeProp = "buildVersionCode"
    }

    object CompileOnly {
        private val nopenVersion = "1.0.1"

        val jetbrainsAnnotations = "org.jetbrains:annotations:20.1.0"
        val nopen = "com.jakewharton.nopen:nopen-annotations:$nopenVersion"
        val nopenChecker = "com.jakewharton.nopen:nopen-checker:$nopenVersion"
        val errorprone = "com.google.errorprone:error_prone_core:2.4.0"
        val errorProneJavac8 = "com.google.errorprone:javac:9+181-r4173-1"
    }

    object Deploy {
        val novodaBintrayPlugin = "com.novoda:bintray-release:1.0.3"
        val novodaBintray = "com.novoda.bintray-release"
        val sign = true
    }

    object NativePlugins {
        val nativeBundlePlugin = "com.ydq.android.gradle.build.tool:nativeBundle:1.0.6"
        val nativeBundleExport = "com.ydq.android.gradle.native-aar.export"
    }
}
