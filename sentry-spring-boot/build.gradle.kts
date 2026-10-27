import io.sentry.gradle.ChildJvmTestArguments
import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  id("io.sentry.animalsniffer")
}

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = JvmTarget.JVM_1_8
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
}

// Spring scans the integration test apps' own bytecode with its repackaged ASM at startup, and
// the ASM in Spring before 5.3.26 fails on class files as new as the toolchain's: "Unsupported
// class file major version 65". In the Spring Boot 2.x matrix, 2.4.13 (Spring 5.3.13) reads up
// to Java 18 and 2.7.0 (Spring 5.3.20) up to Java 19, so the suites target 17. Remove this once
// the matrix drops 2.4.13 and moves 2.7.0 to 2.7.10 or later.
// The suites are Kotlin only. Their Java tasks keep the toolchain's level, because it decides
// which Java version their classpaths ask for, and sentry-test-support needs the toolchain's.
tasks
  .withType<KotlinCompile>()
  .matching { it.name !in setOf("compileKotlin", "compileTestKotlin") }
  .configureEach {
    compilerOptions.jvmTarget = JvmTarget.JVM_17
    jvmTargetValidationMode = org.jetbrains.kotlin.gradle.dsl.jvm.JvmTargetValidationMode.IGNORE
  }

dependencies {
  constraints {
    testImplementation(libs.okhttp) { version { strictly(libs.versions.okhttp.get()) } }
    testImplementation(libs.okhttp.mockwebserver) {
      version { strictly(libs.versions.okhttp.get()) }
    }
  }

  api(projects.sentry)
  api(projects.sentrySpring)
  compileOnly(projects.sentryLogback)
  compileOnly(projects.sentryOkhttp)
  compileOnly(libs.okhttp)
  compileOnly(projects.sentryApacheHttpClient5)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.reactor.core)
  compileOnly(libs.servlet.api)
  compileOnly(libs.springboot.starter)
  compileOnly(libs.springboot.starter.aop)
  compileOnly(libs.spring.graphql)
  compileOnly(libs.springboot.starter.quartz)
  compileOnly(libs.springboot.starter.security)
  compileOnly(libs.spring.kafka2)
  compileOnly(platform(libs.springboot2.bom))
  compileOnly(Config.Libs.springWeb)
  compileOnly(Config.Libs.springWebflux)
  compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryCore)
  compileOnly(projects.sentryGraphql)
  compileOnly(projects.sentryKafka)
  compileOnly(projects.sentryQuartz)

  annotationProcessor(platform(libs.springboot2.bom))
  annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
  annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.sentryLogback)
  testImplementation(projects.sentryOkhttp)
  testImplementation(projects.sentryQuartz)
  testImplementation(projects.sentryApacheHttpClient5)
  testImplementation(projects.sentryKafka)
  testImplementation(projects.sentryTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.google.truth)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.otel)
  testImplementation(libs.otel.extension.autoconfigure.spi)
  testImplementation(libs.springboot.starter)
  testImplementation(libs.springboot.starter.aop)
  testImplementation(libs.springboot.starter.quartz)
  testImplementation(libs.springboot.starter.security)
  testImplementation(libs.spring.kafka2)
  testImplementation(libs.springboot.starter.test)
  testImplementation(libs.springboot.starter.web)
  testImplementation(libs.springboot.starter.webflux)
  testImplementation(libs.springboot3.otel)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryCore)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgent)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
  testImplementation(projects.sentryAsyncProfiler)
}

buildConfig {
  useJavaOutput()
  packageName("io.sentry.spring.boot")
  buildConfigField(
    "String",
    "SENTRY_SPRING_BOOT_SDK_NAME",
    "\"${Config.Sentry.SENTRY_SPRING_BOOT_SDK_NAME}\"",
  )
  buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.withType<JavaCompile>().configureEach {
  dependsOn(tasks.generateBuildConfig)
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.sentry")
  }
}

tasks.jar {
  manifest {
    attributes(
      "Sentry-Version-Name" to project.version,
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_SPRING_BOOT_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-spring-boot",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}

// Spring Boot supports GraphQL only from 2.7, and its Kafka auto-configuration needs 2.7 for the
// spring-kafka version Sentry integrates with. The Spring Boot 2 matrix also covers older versions.
val bootSupportsGraphqlAndKafka =
  libs.versions.springboot2.get().split(".").let { (major, minor) ->
    major.toInt() > 2 || minor.toInt() >= 7
  }

// The OpenTelemetry agent tests run this app in a child JVM, on its own classpath like a real app
// with the agent attached, rather than on the test's classpath.
val agentFixture = sourceSets.create("agentFixture")

// The built agent jar, rather than the plain jar the unit tests compile against.
val otelAgentJar =
  configurations.create("otelAgentJar") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
    attributes {
      attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
      attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
  }

dependencies {
  "agentFixtureImplementation"(platform(libs.springboot2.bom))
  "agentFixtureImplementation"(projects.sentrySpringBootStarter)
  "agentFixtureImplementation"(projects.sentryAsyncProfiler)
  "agentFixtureImplementation"(projects.sentryJdbc)
  "agentFixtureImplementation"(projects.sentryLogback)
  "agentFixtureImplementation"(kotlin(Config.kotlinStdLib))
  "agentFixtureImplementation"(libs.otel)
  "agentFixtureImplementation"(libs.springboot.starter.jdbc)
  "agentFixtureImplementation"(libs.springboot.starter.web)
  "agentFixtureImplementation"(libs.springboot.starter.webflux)
  "agentFixtureRuntimeOnly"(libs.hsqldb)
  if (bootSupportsGraphqlAndKafka) {
    "agentFixtureImplementation"(projects.sentryGraphql)
    "agentFixtureImplementation"(projects.sentryKafka)
    "agentFixtureImplementation"(libs.springboot.starter.graphql)
    "agentFixtureImplementation"(libs.spring.kafka2)
  }

  otelAgentJar(projects.sentryOpentelemetry.sentryOpentelemetryAgent)
}

testing {
  suites {
    // Runs a Spring Boot app in the test's JVM against MockSentryServer. It is a suite of its own
    // because the unit tests' classpath carries the agent, which switches Sentry into agent mode.
    register<JvmTestSuite>("integrationTest") {
      useJUnit()
      dependencies {
        implementation(platform(libs.springboot2.bom))
        implementation(projects.sentrySpringBootStarter)
        implementation(projects.sentryAsyncProfiler)
        implementation(projects.sentryJdbc)
        implementation(projects.sentryLogback)
        implementation(projects.sentryOkhttp)
        implementation(projects.sentryTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.caffeine)
        implementation(libs.okhttp)
        implementation(libs.springboot.starter.aop)
        implementation(libs.springboot.starter.cache)
        implementation(libs.springboot.starter.jdbc)
        implementation(libs.springboot.starter.test)
        implementation(libs.springboot.starter.web)
        implementation(libs.springboot.starter.webflux)
        runtimeOnly(libs.hsqldb)
        if (bootSupportsGraphqlAndKafka) {
          implementation(projects.sentryGraphql)
          implementation(projects.sentryKafka)
          implementation(libs.springboot.starter.graphql)
          implementation(libs.spring.kafka2)
          implementation(libs.spring.kafka2.test)
        }
      }
    }

    // The same for a reactive app: without the servlet stack, Spring Boot starts a WebFlux server.
    register<JvmTestSuite>("integrationTestWebflux") {
      useJUnit()
      dependencies {
        implementation(platform(libs.springboot2.bom))
        implementation(projects.sentrySpringBootStarter)
        implementation(projects.sentryAsyncProfiler)
        implementation(projects.sentryLogback)
        implementation(projects.sentryTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.springboot.starter.test)
        implementation(libs.springboot.starter.webflux)
        if (bootSupportsGraphqlAndKafka) {
          implementation(projects.sentryGraphql)
          implementation(libs.springboot.starter.graphql)
        }
      }
    }

    // The same with Sentry tracing through the OpenTelemetry SDK in the app's JVM instead of the
    // agent.
    register<JvmTestSuite>("integrationTestOtelNoAgent") {
      useJUnit()
      dependencies {
        implementation(platform(libs.springboot2.bom))
        implementation(projects.sentrySpringBootStarter)
        implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentlessSpring)
        implementation(projects.sentryAsyncProfiler)
        implementation(projects.sentryJdbc)
        implementation(projects.sentryLogback)
        implementation(projects.sentryTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.springboot.starter.aop)
        implementation(libs.springboot.starter.jdbc)
        implementation(libs.springboot.starter.test)
        implementation(libs.springboot.starter.web)
        implementation(libs.springboot.starter.webflux)
        runtimeOnly(libs.hsqldb)
        if (bootSupportsGraphqlAndKafka) {
          implementation(projects.sentryGraphql)
          implementation(projects.sentryKafka)
          implementation(libs.springboot.starter.graphql)
          implementation(libs.spring.kafka2)
          implementation(libs.spring.kafka2.test)
        }
      }
    }

    // Runs agentFixture's app with the agent initializing Sentry.
    register<JvmTestSuite>("integrationTestOtelAgent") {
      useJUnit()
      dependencies {
        implementation(platform(libs.springboot2.bom))
        implementation(projects.sentryTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        // For TestRestTemplate. Only the app's JVM runs the agent, so it doesn't trace the tests.
        implementation(libs.springboot.starter.test)
        implementation(libs.springboot.starter.web)
        if (bootSupportsGraphqlAndKafka) {
          implementation(libs.spring.kafka2.test)
        }
      }
      targets.all { testTask.configure { runAgentFixture(autoInit = true) } }
    }
  }
}

/**
 * Has the OpenTelemetry agent tests start agentFixture's app in a child JVM, with the built agent
 * attached. [autoInit] decides whether the agent initializes Sentry or, as with
 * `SENTRY_AUTO_INIT=false`, the Spring Boot starter.
 */
fun Test.runAgentFixture(autoInit: Boolean) {
  jvmArgumentProviders.add(
    objects.newInstance<ChildJvmTestArguments>().apply {
      agentJar.from(otelAgentJar)
      appClasspath.from(agentFixture.runtimeClasspath)
    }
  )
  systemProperty("sentry.test.agentAutoInit", autoInit)
}

// The same tests with the Spring Boot starter initializing Sentry. Each test JVM starts one app, so
// this mode needs a task of its own, which Gradle runs in parallel with the suite's.
tasks.register<Test>("integrationTestOtelAgentWithoutAutoInit") {
  description = "Runs the OpenTelemetry agent tests with SENTRY_AUTO_INIT=false."
  group = LifecycleBasePlugin.VERIFICATION_GROUP
  val sources = sourceSets["integrationTestOtelAgent"]
  testClassesDirs = sources.output.classesDirs
  classpath = sources.runtimeClasspath
  useJUnit()
  runAgentFixture(autoInit = false)
}

// The Spring Boot Gradle plugin compiles apps with parameter names, which Spring reads, for example
// to resolve `#id` in a cache key.
listOf(
    "compileIntegrationTestKotlin",
    "compileIntegrationTestWebfluxKotlin",
    "compileIntegrationTestOtelNoAgentKotlin",
    "compileAgentFixtureKotlin",
    "compileIntegrationTestOtelAgentKotlin",
  )
  .forEach {
    tasks.named<KotlinCompile>(it) {
      compilerOptions.javaParameters = true
      if (!bootSupportsGraphqlAndKafka) {
        exclude("**/Graphql*", "**/Greeting*", "**/Kafka*")
      }
    }
  }
