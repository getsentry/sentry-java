
import java.math.BigDecimal

object Config {
    val AGP = System.getenv("VERSION_AGP") ?: "8.6.0"
    val kotlinStdLib = "stdlib-jdk8"

    val kotlinCompatibleLanguageVersion = "1.6"

    val androidComposeCompilerVersion = "1.5.14"

    object BuildPlugins {
        val androidGradle = "com.android.tools.build:gradle:$AGP"
        val commonsCompressOverride = "org.apache.commons:commons-compress:1.25.0"
    }

    object Android {
        val abiFilters = listOf("x86", "armeabi-v7a", "x86_64", "arm64-v8a")

        fun shouldSkipDebugVariant(name: String?): Boolean {
            return System.getenv("CI")?.toBoolean() ?: false && name == "debug"
        }
    }

    object Libs {
        val okHttpVersion = "4.9.2"
        val timber = "com.jakewharton.timber:timber:4.7.1"
        val okhttp = "com.squareup.okhttp3:okhttp:$okHttpVersion"
        val leakCanary = "com.squareup.leakcanary:leakcanary-android:2.14"

        val slf4jApi = "org.slf4j:slf4j-api:1.7.30"
        val slf4jApi2 = "org.slf4j:slf4j-api:2.0.5"
        val slf4jJdk14 = "org.slf4j:slf4j-jdk14:1.7.30"
        val logbackVersion = "1.2.9"
        val logbackClassic = "ch.qos.logback:logback-classic:$logbackVersion"
        val logbackCore = "ch.qos.logback:logback-core:$logbackVersion"

        val log4j2Version = "2.20.0"
        val log4j2Api = "org.apache.logging.log4j:log4j-api:$log4j2Version"
        val log4j2Core = "org.apache.logging.log4j:log4j-core:$log4j2Version"

        val jacksonDatabind = "com.fasterxml.jackson.core:jackson-databind:2.18.3"
        val jacksonKotlin = "com.fasterxml.jackson.module:jackson-module-kotlin:2.18.3"

        val springWeb = "org.springframework:spring-webmvc"
        val springWebflux = "org.springframework:spring-webflux"
        val springSecurityWeb = "org.springframework.security:spring-security-web"
        val springSecurityConfig = "org.springframework.security:spring-security-config"
        val springAop = "org.springframework:spring-aop"
        val aspectj = "org.aspectj:aspectjweaver"
        val servletApi = "javax.servlet:javax.servlet-api:3.1.0"
        val servletApiJakarta = "jakarta.servlet:jakarta.servlet-api:5.0.0"

        val apacheHttpClient = "org.apache.httpcomponents.client5:httpclient5:5.0.4"

        private val retrofit2Version = "2.9.0"
        private val retrofit2Group = "com.squareup.retrofit2"
        val retrofit2 = "$retrofit2Group:retrofit:$retrofit2Version"
        val retrofit2Gson = "$retrofit2Group:converter-gson:$retrofit2Version"

        val coroutinesCore = "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.1"

        val coroutinesAndroid = "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.6.1"

        val reactorCore = "io.projectreactor:reactor-core:3.5.3"
        val contextPropagation = "io.micrometer:context-propagation:1.1.0"

        private val feignVersion = "11.6"
        val feignCore = "io.github.openfeign:feign-core:$feignVersion"
        val feignGson = "io.github.openfeign:feign-gson:$feignVersion"

        private val apolloVersion = "2.5.9"
        val apolloAndroid = "com.apollographql.apollo:apollo-runtime:$apolloVersion"
        val apolloCoroutines = "com.apollographql.apollo:apollo-coroutines-support:$apolloVersion"

        val p6spy = "p6spy:p6spy:3.9.1"

        val graphQlJava = "com.graphql-java:graphql-java:17.3"
        val graphQlJava22 = "com.graphql-java:graphql-java:22.1"
        val graphQlJavaNew = "com.graphql-java:graphql-java:24.0"

        val quartz = "org.quartz-scheduler:quartz:2.3.0"

        val kotlinReflect = "org.jetbrains.kotlin:kotlin-reflect"
        val kotlinStdLib = "org.jetbrains.kotlin:kotlin-stdlib"

        val apolloKotlin = "com.apollographql.apollo3:apollo-runtime:3.8.2"
        val apolloKotlin4 = "com.apollographql.apollo:apollo-runtime:4.1.1"

        val sentryNativeNdk = "io.sentry:sentry-native-ndk:0.8.4"
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
        val SENTRY_SPRING_BOOT_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot"
        val SENTRY_SPRING_BOOT_STARTER_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot-starter"
        val SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot.jakarta"
        val SENTRY_SPRING_BOOT_STARTER_JAKARTA_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.spring-boot-starter.jakarta"
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
        val SENTRY_SERVLET_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.servlet"
        val SENTRY_SERVLET_JAKARTA_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.servlet.jakarta"
        val SENTRY_COMPOSE_HELPER_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.compose.helper"
        val SENTRY_OKHTTP_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.okhttp"
        val SENTRY_REACTOR_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.reactor"
        val SENTRY_KOTLIN_EXTENSIONS_SDK_NAME = "$SENTRY_JAVA_SDK_NAME.kotlin-extensions"
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
