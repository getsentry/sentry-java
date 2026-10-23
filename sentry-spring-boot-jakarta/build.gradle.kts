import io.sentry.gradle.ChildJvmTestArguments
import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.springboot3) apply false
}

// Published code targets Java 17. Test code is not published and follows the toolchain.
tasks.named<JavaCompile>("compileJava") { options.release.set(17) }

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
}

dependencies {
  constraints {
    testImplementation(libs.okhttp) { version { strictly(libs.versions.okhttp.get()) } }
    testImplementation(libs.okhttp.mockwebserver) {
      version { strictly(libs.versions.okhttp.get()) }
    }
  }

  api(projects.sentry)
  api(projects.sentrySpringJakarta)
  compileOnly(projects.sentryLogback)
  compileOnly(projects.sentryLog4j2)
  compileOnly(projects.sentryOkhttp)
  compileOnly(libs.okhttp)
  compileOnly(projects.sentryApacheHttpClient5)
  compileOnly(libs.log4j.api)
  compileOnly(libs.log4j.core)
  compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))
  compileOnly(projects.sentryGraphql)
  compileOnly(projects.sentryGraphql22)
  compileOnly(projects.sentryQuartz)
  compileOnly(libs.spring.kafka3)
  compileOnly(Config.Libs.springWeb)
  compileOnly(Config.Libs.springWebflux)
  compileOnly(libs.context.propagation)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.otel)
  compileOnly(libs.reactor.core)
  compileOnly(libs.servlet.jakarta.api)
  compileOnly(libs.springboot3.starter)
  compileOnly(libs.springboot3.starter.aop)
  compileOnly(libs.springboot3.starter.graphql)
  compileOnly(libs.springboot3.starter.quartz)
  compileOnly(libs.springboot3.starter.security)
  compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryCore)
  compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
  api(projects.sentryReactor)

  annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
  annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
  annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.sentryLogback)
  testImplementation(projects.sentryOkhttp)
  testImplementation(projects.sentryLog4j2)
  testImplementation(projects.sentryApacheHttpClient5)
  testImplementation(projects.sentryGraphql)
  testImplementation(projects.sentryGraphql22)
  testImplementation(projects.sentryKafka)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryCore)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgent)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
  testImplementation(projects.sentryQuartz)
  testImplementation(projects.sentryReactor)
  testImplementation(projects.sentryTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
  testImplementation(libs.context.propagation)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.google.truth)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.okhttp)
  testImplementation(libs.log4j.api)
  testImplementation(libs.log4j.core)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.otel)
  testImplementation(libs.otel.extension.autoconfigure.spi)
  testImplementation(libs.springboot3.otel)
  testImplementation(libs.springboot3.starter)
  testImplementation(libs.springboot3.starter.aop)
  testImplementation(libs.springboot3.starter.graphql)
  testImplementation(libs.spring.kafka3)
  testImplementation(libs.springboot3.starter.quartz)
  testImplementation(libs.springboot3.starter.security)
  testImplementation(libs.springboot3.starter.test)
  testImplementation(libs.springboot3.starter.web)
  testImplementation(libs.springboot3.starter.webflux)
  testImplementation(projects.sentryAsyncProfiler)
}

buildConfig {
  useJavaOutput()
  packageName("io.sentry.spring.boot.jakarta")
  buildConfigField(
    "String",
    "SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME",
    "\"${Config.Sentry.SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME}\"",
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
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_SPRING_BOOT_JAKARTA_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-spring-boot-jakarta",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
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
  "agentFixtureImplementation"(platform(SpringBootPlugin.BOM_COORDINATES))
  "agentFixtureImplementation"(projects.sentrySpringBootStarterJakarta)
  "agentFixtureImplementation"(projects.sentryAsyncProfiler)
  "agentFixtureImplementation"(projects.sentryGraphql22)
  "agentFixtureImplementation"(projects.sentryJdbc)
  "agentFixtureImplementation"(projects.sentryKafka)
  "agentFixtureImplementation"(projects.sentryLogback)
  "agentFixtureImplementation"(kotlin(Config.kotlinStdLib))
  // Reactor's automatic context propagation needs it to carry the Sentry scope across threads.
  "agentFixtureImplementation"(libs.context.propagation)
  "agentFixtureImplementation"(libs.caffeine)
  "agentFixtureImplementation"(libs.otel)
  "agentFixtureImplementation"(libs.springboot3.starter.cache)
  "agentFixtureImplementation"(libs.springboot3.starter.graphql)
  "agentFixtureImplementation"(libs.springboot3.starter.jdbc)
  "agentFixtureImplementation"(libs.springboot3.starter.web)
  "agentFixtureImplementation"(libs.springboot3.starter.webflux)
  "agentFixtureImplementation"(libs.spring.kafka3)
  "agentFixtureRuntimeOnly"(libs.hsqldb)

  otelAgentJar(projects.sentryOpentelemetry.sentryOpentelemetryAgent)
}

// Swaps Logback for Log4j2 where a classpath has both starters, which only
// integrationTestLog4j2's has.
dependencies {
  modules {
    module("org.springframework.boot:spring-boot-starter-logging") {
      replacedBy(
        "org.springframework.boot:spring-boot-starter-log4j2",
        "Use Log4j2 instead of Logback",
      )
    }
  }
}

testing {
  suites {
    // Runs a Spring Boot app in the test's JVM against MockSentryServer. It is a suite of its own
    // because the unit tests' classpath carries the agent, which switches Sentry into agent mode.
    register<JvmTestSuite>("integrationTest") {
      useJUnit()
      dependencies {
        implementation(platform(SpringBootPlugin.BOM_COORDINATES))
        implementation(projects.sentrySpringBootStarterJakarta)
        implementation(projects.sentryAsyncProfiler)
        implementation(projects.sentryGraphql22)
        implementation(projects.sentryJdbc)
        implementation(projects.sentryKafka)
        implementation(projects.sentryLogback)
        implementation(projects.sentryOkhttp)
        implementation(projects.sentryOpenfeature)
        implementation(projects.sentryTestSupport)
        // Reactor's automatic context propagation needs it to carry the Sentry scope across
        // threads.
        // The sample only had it because Spring GraphQL pulls it in.
        implementation(libs.context.propagation)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.caffeine)
        implementation(libs.okhttp)
        implementation(libs.openfeature)
        implementation(libs.springboot3.starter.aop)
        implementation(libs.springboot3.starter.cache)
        implementation(libs.springboot3.starter.graphql)
        implementation(libs.springboot3.starter.jdbc)
        implementation(libs.springboot3.starter.test)
        implementation(libs.springboot3.starter.web)
        implementation(libs.springboot3.starter.webflux)
        implementation(libs.spring.kafka3)
        implementation(libs.spring.kafka3.test)
        runtimeOnly(libs.hsqldb)
      }
    }

    // The servlet app with Log4j2 instead of Logback, so Sentry's auto-configuration attaches its
    // Log4j2 appender.
    register<JvmTestSuite>("integrationTestLog4j2") {
      useJUnit()
      dependencies {
        implementation(platform(SpringBootPlugin.BOM_COORDINATES))
        implementation(projects.sentrySpringBootStarterJakarta)
        implementation(projects.sentryLog4j2)
        implementation(projects.sentryTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation("org.springframework.boot:spring-boot-starter-log4j2")
        implementation(libs.springboot3.starter.test)
        implementation(libs.springboot3.starter.web)
      }
    }

    // The same for a reactive app: without the servlet stack, Spring Boot starts a WebFlux server.
    register<JvmTestSuite>("integrationTestWebflux") {
      useJUnit()
      dependencies {
        implementation(platform(SpringBootPlugin.BOM_COORDINATES))
        implementation(projects.sentrySpringBootStarterJakarta)
        implementation(projects.sentryAsyncProfiler)
        implementation(projects.sentryGraphql22)
        implementation(projects.sentryLogback)
        implementation(projects.sentryTestSupport)
        // Reactor's automatic context propagation needs it to carry the Sentry scope across
        // threads.
        implementation(libs.context.propagation)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.caffeine)
        implementation(libs.springboot3.starter.cache)
        implementation(libs.springboot3.starter.graphql)
        implementation(libs.springboot3.starter.test)
        implementation(libs.springboot3.starter.webflux)
      }
    }

    // The same with Sentry tracing through the OpenTelemetry SDK in the app's JVM instead of the
    // agent.
    register<JvmTestSuite>("integrationTestOtelNoAgent") {
      useJUnit()
      dependencies {
        implementation(platform(SpringBootPlugin.BOM_COORDINATES))
        implementation(projects.sentrySpringBootStarterJakarta)
        implementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentlessSpring)
        implementation(projects.sentryAsyncProfiler)
        implementation(projects.sentryGraphql22)
        implementation(projects.sentryJdbc)
        implementation(projects.sentryKafka)
        implementation(projects.sentryLogback)
        implementation(projects.sentryTestSupport)
        // Reactor's automatic context propagation needs it to carry the Sentry scope across
        // threads.
        implementation(libs.context.propagation)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.caffeine)
        implementation(libs.springboot3.starter.aop)
        implementation(libs.springboot3.starter.cache)
        implementation(libs.springboot3.starter.graphql)
        implementation(libs.springboot3.starter.jdbc)
        implementation(libs.springboot3.starter.test)
        implementation(libs.springboot3.starter.web)
        implementation(libs.springboot3.starter.webflux)
        implementation(libs.spring.kafka3)
        implementation(libs.spring.kafka3.test)
        runtimeOnly(libs.hsqldb)
      }
    }

    // Runs agentFixture's app with the agent initializing Sentry.
    register<JvmTestSuite>("integrationTestOtelAgent") {
      useJUnit()
      dependencies {
        implementation(platform(SpringBootPlugin.BOM_COORDINATES))
        implementation(projects.sentryTestSupport)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        // For TestRestTemplate. Only the app's JVM runs the agent, so it doesn't trace the tests.
        implementation(libs.springboot3.starter.test)
        implementation(libs.springboot3.starter.web)
        implementation(libs.spring.kafka3.test)
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
  )
  .forEach {
    tasks.named<KotlinCompile>(it) { compilerOptions.javaParameters = true }
  }
