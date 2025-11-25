
import java.math.BigDecimal

object Config {
    val AGP = System.getenv("VERSION_AGP") ?: "8.6.0"
    val kotlinStdLib = "stdlib-jdk8"
    val kotlinStdLibVersionAndroid = "1.9.24"
    val kotlinTestJunit = "test-junit"

    object BuildPlugins {
        val androidGradle = "com.android.tools.build:gradle:$AGP"
    }

    object Android {
        val abiFilters = listOf("x86", "armeabi-v7a", "x86_64", "arm64-v8a")

        fun shouldSkipDebugVariant(name: String?): Boolean {
            return System.getenv("CI")?.toBoolean() ?: false && name == "debug"
        }
    }

    object Libs {
        val springWeb = "org.springframework:spring-webmvc"
        val springWebflux = "org.springframework:spring-webflux"
        val springSecurityWeb = "org.springframework.security:spring-security-web"
        val springSecurityConfig = "org.springframework.security:spring-security-config"
        val springAop = "org.springframework:spring-aop"
        val aspectj = "org.aspectj:aspectjweaver"

        val kotlinReflect = "org.jetbrains.kotlin:kotlin-reflect"
        val kotlinStdLib = "org.jetbrains.kotlin:kotlin-stdlib"
    }

    object AnnotationProcessors {
        val springBootAutoConfigure = "org.springframework.boot:spring-boot-autoconfigure-processor"
        val springBootConfiguration = "org.springframework.boot:spring-boot-configuration-processor"
    }

    object QualityPlugins {
        object Jacoco {
            // TODO [POTEL] add tests and restore
            val minimumCoverage = BigDecimal.valueOf(0.1)
        }

        // this can be removed when we upgrade to Gradle 8, which allows us to use a getter for the plugin ID
        val detektPlugin = "io.gitlab.arturbosch.detekt"
    }

    object Sentry {
        val SENTRY_JAVA_SDK_NAME = "sentry.java"
        val SENTRY_ANDROID_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.android"
        val SENTRY_TIMBER_SDK_NAME = "$SENTRY_ANDROID_SDK_NAME.timber"
        val SENTRY_LOGBACK_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.logback"
        val SENTRY_JUL_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.jul"
        val SENTRY_LOG4J2_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.log4j2"
        val SENTRY_SPRING_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring"
        val SENTRY_SPRING_JAKARTA_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring.jakarta"
        val SENTRY_SPRING_7_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-7"
        val SENTRY_SPRING_BOOT_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot"
        val SENTRY_SPRING_BOOT_STARTER_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot-starter"
        val SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot.jakarta"
        val SENTRY_SPRING_BOOT_STARTER_JAKARTA_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot-starter.jakarta"
        val SENTRY_SPRING_BOOT_4_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot-4"
        val SENTRY_SPRING_BOOT_4_STARTER_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot-4-starter"
        val SENTRY_OPENTELEMETRY_BOOTSTRAP_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.opentelemetry.bootstrap"
        val SENTRY_OPENTELEMETRY_CORE_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.opentelemetry.core"
        val SENTRY_OPENTELEMETRY_AGENT_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.opentelemetry.agent"
        val SENTRY_OPENTELEMETRY_AGENTLESS_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.opentelemetry.agentless"
        val SENTRY_OPENTELEMETRY_AGENTLESS_SPRING_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.opentelemetry.agentless-spring"
        val SENTRY_OPENTELEMETRY_AGENTCUSTOMIZATION_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.opentelemetry.agentcustomization"
        val SENTRY_OPENFEIGN_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.openfeign"
        val SENTRY_APOLLO3_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.apollo3"
        val SENTRY_APOLLO4_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.apollo4"
        val SENTRY_APOLLO_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.apollo"
        val SENTRY_GRAPHQL_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.graphql"
        val SENTRY_GRAPHQL_CORE_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.graphql-core"
        val SENTRY_GRAPHQL22_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.graphql22"
        val SENTRY_QUARTZ_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.quartz"
        val SENTRY_JDBC_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.jdbc"
        val SENTRY_OPENFEATURE_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.openfeature"
        val SENTRY_LAUNCHDARKLY_SERVER_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.launchdarkly-server"
        val SENTRY_LAUNCHDARKLY_ANDROID_SDK_NAME = "$SENTRY_ANDROID_SDK_NAME.launchdarkly"
        val SENTRY_SERVLET_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.servlet"
        val SENTRY_SERVLET_JAKARTA_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.servlet.jakarta"
        val SENTRY_COMPOSE_HELPER_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.compose.helper"
        val SENTRY_OKHTTP_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.okhttp"
        val SENTRY_REACTOR_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.reactor"
        val SENTRY_KOTLIN_EXTENSIONS_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.kotlin-extensions"
        val SENTRY_ASYNC_PROFILER_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.async-profiler"
        val SENTRY_KTOR_CLIENT_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.ktor-client"
        val group = "io.sentry"
        val description = "SDK for sentry.io"
        val versionNameProp = "versionName"
    }

    object BuildScript {
        val androidLibs = setOf(
            "sentry-android-core",
            "sentry-android-ndk",
            "sentry-android-fragment",
            "sentry-android-navigation",
            "sentry-android-timber",
            "sentry-compose-android",
            "sentry-android-sqlite",
            "sentry-android-replay"
        )

        val androidXLibs = listOf(
            "androidx.core:core"
        )
    }
}
