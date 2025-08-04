import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  `java-library`
  id("io.sentry.javadoc")
  kotlin("jvm")
  jacoco
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.springboot4) apply false
}

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions {
    jvmTarget = JavaVersion.VERSION_17.toString()
    languageVersion = libs.versions.kotlin.compatible.version.get()
    freeCompilerArgs = listOf("-Xjsr305=strict")
  }
}

dependencies {
  api(projects.sentry)
  compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))
  compileOnly(Config.Libs.springWeb)
  compileOnly(Config.Libs.springAop)
  compileOnly(Config.Libs.springSecurityWeb)
  compileOnly(Config.Libs.aspectj)
  compileOnly(libs.context.propagation)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.otel)
  compileOnly(libs.servlet.jakarta.api)
  compileOnly(libs.slf4j.api)
  compileOnly(libs.springboot4.starter.graphql)
  compileOnly(libs.springboot4.starter.quartz)

  compileOnly(Config.Libs.springWebflux)
  compileOnly(projects.sentryGraphql)
  compileOnly(projects.sentryGraphql22)
  compileOnly(projects.sentryQuartz)
  compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
  compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
  api(projects.sentryReactor)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.sentryTestSupport)
  testImplementation(projects.sentryGraphql)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.context.propagation)
  testImplementation(libs.graphql.java24)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
  testImplementation(libs.springboot4.starter.aop)
  testImplementation(libs.springboot4.starter.graphql)
  testImplementation(libs.springboot4.starter.security)
  testImplementation(libs.springboot4.starter.test)
  testImplementation(libs.springboot4.starter.web)
  testImplementation(libs.springboot4.starter.webflux)
  testImplementation(projects.sentryReactor)
}

configure<SourceSetContainer> { test { java.srcDir("src/test/java") } }

jacoco { toolVersion = libs.versions.jacoco.get() }

tasks.jacocoTestReport {
  reports {
    xml.required.set(true)
    html.required.set(false)
  }
}

tasks {
  jacocoTestCoverageVerification {
    violationRules { rule { limit { minimum = Config.QualityPlugins.Jacoco.minimumCoverage } } }
  }
  check {
    dependsOn(jacocoTestCoverageVerification)
    dependsOn(jacocoTestReport)
  }
}

buildConfig {
  useJavaOutput()
  packageName("io.sentry.spring7")
  buildConfigField(
    "String",
    "SENTRY_SPRING_7_SDK_NAME",
    "\"${Config.Sentry.SENTRY_SPRING_7_SDK_NAME}\"",
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
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_SPRING_7_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-spring-7",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
