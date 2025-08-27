import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  jacoco
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.springboot2) apply false
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
}

dependencies {
  api(projects.sentry)
  api(projects.sentrySpring)
  compileOnly(projects.sentryLogback)
  compileOnly(projects.sentryApacheHttpClient5)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.reactor.core)
  compileOnly(libs.servlet.api)
  compileOnly(libs.springboot.starter)
  compileOnly(libs.springboot.starter.aop)
  compileOnly(libs.springboot.starter.graphql)
  compileOnly(libs.springboot.starter.quartz)
  compileOnly(libs.springboot.starter.security)
  compileOnly(platform(SpringBootPlugin.BOM_COORDINATES))
  compileOnly(Config.Libs.springWeb)
  compileOnly(Config.Libs.springWebflux)
  compileOnly(projects.sentryOpentelemetry.sentryOpentelemetryCore)
  compileOnly(projects.sentryGraphql)
  compileOnly(projects.sentryQuartz)

  annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
  annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
  annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.sentryLogback)
  testImplementation(projects.sentryQuartz)
  testImplementation(projects.sentryApacheHttpClient5)
  testImplementation(projects.sentryTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.otel)
  testImplementation(libs.otel.extension.autoconfigure.spi)
  testImplementation(libs.springboot.starter)
  testImplementation(libs.springboot.starter.aop)
  testImplementation(libs.springboot.starter.quartz)
  testImplementation(libs.springboot.starter.security)
  testImplementation(libs.springboot.starter.test)
  testImplementation(libs.springboot.starter.web)
  testImplementation(libs.springboot.starter.webflux)
  testImplementation(libs.springboot3.otel)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryCore)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgent)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
  testImplementation(projects.sentryOpentelemetry.sentryOpentelemetryBootstrap)
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
