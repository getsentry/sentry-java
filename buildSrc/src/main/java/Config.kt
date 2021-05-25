import java.math.BigDecimal
import java.util.Locale

object Config {
    val kotlinVersion = "1.4.30"
    val kotlinStdLib = "stdlib-jdk8"

    val springBootVersion = "2.4.4"
    // Spring is currently not compatible with Kotlin 1.4
    val springKotlinCompatibleLanguageVersion = "1.3"

    object BuildPlugins {
        val androidGradle = "com.android.tools.build:gradle:4.2.0"
        val kotlinGradlePlugin = "gradle-plugin"
        val buildConfig = "com.github.gmazzo.buildconfig"
        val buildConfigVersion = "3.0.0"
        val springBoot = "org.springframework.boot"
        val springDependencyManagement = "io.spring.dependency-management"
        val springDependencyManagementVersion = "1.0.11.RELEASE"
        val gretty = "org.gretty"
        val grettyVersion = "3.0.4"
        val gradleMavenPublishPlugin = "com.vanniktech:gradle-maven-publish-plugin:0.15.1"
        val dokkaPlugin = "org.jetbrains.dokka:dokka-gradle-plugin:$kotlinVersion"

        fun shouldSignArtifacts(version: String): Boolean {
            return !(System.getenv("CI")?.toBoolean() ?: false) &&
                    !version.toUpperCase(Locale.ROOT).endsWith("SNAPSHOT")
        }
    }

    object Android {
        private val sdkVersion = 30

        val minSdkVersion = 14
        val minSdkVersionOkHttp = 21
        val minSdkVersionNdk = 16
        val targetSdkVersion = sdkVersion
        val compileSdkVersion = sdkVersion

        val abiFilters = listOf("x86", "armeabi-v7a", "x86_64", "arm64-v8a")
    }

    object Libs {
        val appCompat = "androidx.appcompat:appcompat:1.2.0"
        val timber = "com.jakewharton.timber:timber:4.7.1"
        val okhttpBom = "com.squareup.okhttp3:okhttp-bom:4.9.0"
        val okhttp = "com.squareup.okhttp3:okhttp"
        // only bump gson if https://github.com/google/gson/issues/1597 is fixed
        val gson = "com.google.code.gson:gson:2.8.5"
        val leakCanary = "com.squareup.leakcanary:leakcanary-android:2.6"

        private val lifecycleVersion = "2.2.0"
        val lifecycleProcess = "androidx.lifecycle:lifecycle-process:$lifecycleVersion"
        val lifecycleCommonJava8 = "androidx.lifecycle:lifecycle-common-java8:$lifecycleVersion"

        val slf4jApi = "org.slf4j:slf4j-api:1.7.30"
        val logbackVersion = "1.2.3"
        val logbackClassic = "ch.qos.logback:logback-classic:$logbackVersion"

        val log4j2Version = "2.13.3"
        val log4j2Api = "org.apache.logging.log4j:log4j-api:$log4j2Version"
        val log4j2Core = "org.apache.logging.log4j:log4j-core:$log4j2Version"

        val springBootStarter = "org.springframework.boot:spring-boot-starter:$springBootVersion"
        val springBootStarterTest = "org.springframework.boot:spring-boot-starter-test:$springBootVersion"
        val springBootStarterWeb = "org.springframework.boot:spring-boot-starter-web:$springBootVersion"
        val springBootStarterAop = "org.springframework.boot:spring-boot-starter-aop:$springBootVersion"
        val springBootStarterSecurity = "org.springframework.boot:spring-boot-starter-security:$springBootVersion"

        val springWeb = "org.springframework:spring-webmvc"
        val springSecurityWeb = "org.springframework.security:spring-security-web"
        val springSecurityConfig = "org.springframework.security:spring-security-config"
        val springAop = "org.springframework:spring-aop"
        val aspectj = "org.aspectj:aspectjweaver"
        val servletApi = "javax.servlet:javax.servlet-api"

        val apacheHttpClient = "org.apache.httpcomponents.client5:httpclient5:5.0.4"

        private val retrofit2Version = "2.9.0"
        private val retrofit2Group = "com.squareup.retrofit2"
        val retrofit2 = "$retrofit2Group:retrofit:$retrofit2Version"
        val retrofit2Gson = "$retrofit2Group:converter-gson:$retrofit2Version"

        val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.3"
    }

    object AnnotationProcessors {
        val springBootAutoConfigure = "org.springframework.boot:spring-boot-autoconfigure-processor"
        val springBootConfiguration = "org.springframework.boot:spring-boot-configuration-processor"
    }

    object TestLibs {
        private val androidxTestVersion = "1.3.0"

        val kotlinTestJunit = "org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion"
        val androidxCore = "androidx.test:core:$androidxTestVersion"
        val androidxRunner = "androidx.test:runner:$androidxTestVersion"
        val androidxJunit = "androidx.test.ext:junit:1.1.2"
        val androidxCoreKtx = "androidx.core:core-ktx:1.3.2"
        val robolectric = "org.robolectric:robolectric:4.5.1"
        val mockitoKotlin = "com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0"
        val mockitoInline = "org.mockito:mockito-inline:3.8.0"
        val awaitility = "org.awaitility:awaitility-kotlin:4.0.3"
        val mockWebserver = "com.squareup.okhttp3:mockwebserver:4.9.0"
        val jsonUnit = "net.javacrumbs.json-unit:json-unit:2.11.1"
    }

    object QualityPlugins {
        object Jacoco {
            val version = "0.8.6"
            val minimumCoverage = BigDecimal.valueOf(0.6)
        }
        val spotless = "com.diffplug.spotless"
        val spotlessVersion = "5.11.0"
        val errorProne = "net.ltgt.errorprone"
        val errorpronePlugin = "net.ltgt.gradle:gradle-errorprone-plugin:1.3.0"
        val gradleVersionsPlugin = "com.github.ben-manes:gradle-versions-plugin:0.36.0"
        val gradleVersions = "com.github.ben-manes.versions"
        val detekt = "io.gitlab.arturbosch.detekt"
        // use RC2 to drop jcenter because of kotlinx-html
        val detektVersion = "1.17.0-RC2"
        val detektPlugin = "io.gitlab.arturbosch.detekt"
        val binaryCompatibilityValidatorPlugin = "org.jetbrains.kotlinx:binary-compatibility-validator:0.5.0"
        val binaryCompatibilityValidator = "binary-compatibility-validator"
    }

    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
        val SENTRY_ANDROID_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.android"
        val SENTRY_TIMBER_SDK_NAME = "$SENTRY_ANDROID_SDK_NAME.timber"
        val SENTRY_LOGBACK_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.logback"
        val SENTRY_JUL_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.jul"
        val SENTRY_LOG4J2_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.log4j2"
        val SENTRY_SPRING_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring"
        val SENTRY_SPRING_BOOT_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot"
        val group = "io.sentry"
        val description = "SDK for sentry.io"
        val versionNameProp = "versionName"
        val buildVersionCodeProp = "buildVersionCode"
    }

    object CompileOnly {
        private val nopenVersion = "1.0.1"

        val jetbrainsAnnotations = "org.jetbrains:annotations:20.1.0"
        val nopen = "com.jakewharton.nopen:nopen-annotations:$nopenVersion"
        val nopenChecker = "com.jakewharton.nopen:nopen-checker:$nopenVersion"
        val errorprone = "com.google.errorprone:error_prone_core:2.5.1"
        val errorProneJavac8 = "com.google.errorprone:javac:9+181-r4173-1"
        val errorProneNullAway = "com.uber.nullaway:nullaway:0.9.1"
    }

    object NativePlugins {
        val nativeBundlePlugin = "com.ydq.android.gradle.build.tool:nativeBundle:1.0.7"
        val nativeBundleExport = "com.ydq.android.gradle.native-aar.export"
    }
}
