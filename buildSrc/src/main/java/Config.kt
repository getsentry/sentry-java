object Config {
    val kotlinVersion = "1.3.72"
    val kotlinStdLib = "stdlib-jdk8"

    val springBootVersion = "2.3.3.RELEASE"

    object BuildPlugins {
        val androidGradle = "com.android.tools.build:gradle:4.0.1"
        val kotlinGradlePlugin = "gradle-plugin"
        val buildConfig = "com.github.gmazzo.buildconfig"
        val buildConfigVersion = "2.0.2"
        val springBoot = "org.springframework.boot"
        val springDependencyManagement = "io.spring.dependency-management"
        val springDependencyManagementVersion = "1.0.10.RELEASE"
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
        val appCompat = "androidx.appcompat:appcompat:1.1.0"
        val timber = "com.jakewharton.timber:timber:4.7.1"
        // only bump gson if https://github.com/google/gson/issues/1597 is fixed
        val gson = "com.google.code.gson:gson:2.8.5"
        val leakCanary = "com.squareup.leakcanary:leakcanary-android:2.3"

        val lifecycleVersion = "2.2.0"
        val lifecycleProcess = "androidx.lifecycle:lifecycle-process:$lifecycleVersion"
        val lifecycleCommonJava8 = "androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion"

        val logbackVersion = "1.2.3"
        val logbackClassic = "ch.qos.logback:logback-classic:$logbackVersion"

        val log4j2Version = "2.13.3"
        val log4j2Api = "org.apache.logging.log4j:log4j-api:$log4j2Version"
        val log4j2Core = "org.apache.logging.log4j:log4j-core:$log4j2Version"

        val springBootStarter = "org.springframework.boot:spring-boot-starter:$springBootVersion"

        val springWeb = "org.springframework:spring-webmvc"
        val servletApi = "javax.servlet:javax.servlet-api"
    }

    object AnnotationProcessors {
        val springBootAutoConfigure = "org.springframework.boot:spring-boot-autoconfigure-processor"
        val springBootConfiguration = "org.springframework.boot:spring-boot-configuration-processor"
    }

    object TestLibs {
        private val androidxTestVersion = "1.2.0"

        val kotlinTestJunit = "org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion"
        val androidxCore = "androidx.test:core:$androidxTestVersion"
        val androidxRunner = "androidx.test:runner:$androidxTestVersion"
        val androidxJunit = "androidx.test.ext:junit:1.1.1"
        val robolectric = "org.robolectric:robolectric:4.3.1"
        val mockitoKotlin = "com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0"
        val awaitility = "org.awaitility:awaitility-kotlin:4.0.3"
        val springBootStarterTest = "org.springframework.boot:spring-boot-starter-test:$springBootVersion"
        val springBootStarterWeb = "org.springframework.boot:spring-boot-starter-web:$springBootVersion"
        val springBootStarterSecurity = "org.springframework.boot:spring-boot-starter-security:$springBootVersion"
    }

    object QualityPlugins {
        val jacocoVersion = "0.8.5"
        val spotless = "com.diffplug.spotless"
        val spotlessVersion = "5.1.0"
        val errorProne = "net.ltgt.errorprone"
        val errorpronePlugin = "net.ltgt.gradle:gradle-errorprone-plugin:1.2.1"
        val gradleVersionsPlugin = "com.github.ben-manes:gradle-versions-plugin:0.29.0"
        val gradleVersions = "com.github.ben-manes.versions"
        val detekt = "io.gitlab.arturbosch.detekt"
        val detektVersion = "1.10.0"
        val detektPlugin = "io.gitlab.arturbosch.detekt"
    }

    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
        val SENTRY_ANDROID_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.android"
        val SENTRY_LOGBACK_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.logback"
        val SENTRY_LOG4J2_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.log4j2"
        val SENTRY_SPRING_BOOT_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot"
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

        val jetbrainsAnnotations = "org.jetbrains:annotations:20.0.0"
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
