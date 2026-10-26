import io.sentry.gradle.SpringBootVersions
import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.sentry.javadoc")
  id("io.sentry.spring-boot-matrix")
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

dependencies {
  api(projects.sentry)

  compileOnly(platform(libs.springboot2.bom))
  compileOnly(Config.Libs.springWeb)
  compileOnly(Config.Libs.springAop)
  compileOnly(Config.Libs.springSecurityWeb)
  compileOnly(Config.Libs.aspectj)
  compileOnly(Config.Libs.springWebflux)
  compileOnly(projects.sentryGraphql)
  compileOnly(projects.sentryKafka)
  compileOnly(projects.sentryQuartz)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.otel)
  compileOnly(libs.servlet.api)
  compileOnly(libs.slf4j.api)
  compileOnly(libs.spring.graphql)
  compileOnly(libs.springboot.starter.quartz)
  compileOnly(libs.spring.kafka2)
  compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
  compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.sentryTestSupport)
  testImplementation(projects.sentryGraphql)
  testImplementation(projects.sentryKafka)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.graphql.java17)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.core)
  testImplementation(libs.springboot.starter.aop)
  testImplementation(libs.springboot.starter.graphql)
  testImplementation(libs.springboot.starter.security)
  testImplementation(libs.spring.kafka2)
  testImplementation(libs.springboot.starter.test)
  testImplementation(libs.springboot.starter.web)
  testImplementation(libs.springboot.starter.webflux)
}

buildConfig {
  useJavaOutput()
  packageName("io.sentry.spring")
  buildConfigField(
    "String",
    "SENTRY_SPRING_SDK_NAME",
    "\"${Config.Sentry.SENTRY_SPRING_SDK_NAME}\"",
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
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_SPRING_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-spring",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}

testing {
  suites {
    // Runs a plain Spring MVC app in an embedded Tomcat against MockSentryServer, the way a servlet
    // container runs a WAR. Spring Boot isn't on its classpath, because Sentry behaves differently
    // with it.
    register<JvmTestSuite>("integrationTest") {
      useJUnit()
      dependencies {
        implementation(platform(libs.springboot2.bom))
        implementation(project())
        implementation(projects.sentryAsyncProfiler)
        implementation(projects.sentryTestSupport)
        implementation(Config.Libs.springWeb)
        implementation(libs.jackson.databind)
        implementation(libs.kotlin.test.junit)
        implementation(libs.google.truth)
        implementation(libs.tomcat.embed.core)
      }
    }
  }
}

// The suites on each Spring Boot 2 version the CI matrix tests.
springBootMatrix {
  versions(SpringBootVersions.BOOT_2)
  test("integrationTest")
}
