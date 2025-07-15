import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  kotlin("jvm")
  jacoco
  id("io.sentry.javadoc")
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

kotlin { explicitApi() }

dependencies {
  api(projects.sentry)

  api(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  api(projects.sentryKotlinExtensions)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.ktor.client.core)
  compileOnly(libs.ktor.client.okhttp)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  testImplementation(projects.sentryTestSupport)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
  testImplementation(libs.ktor.client.core)
  testImplementation(libs.ktor.client.java)
  testImplementation(libs.ktor.client.okhttp)
  testImplementation(projects.sentryOkhttp)
  testImplementation(libs.okhttp.mockwebserver)
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
  packageName("io.sentry.ktorClient")
  buildConfigField(
    "String",
    "SENTRY_KTOR_CLIENT_SDK_NAME",
    "\"${Config.Sentry.SENTRY_KTOR_CLIENT_SDK_NAME}\"",
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
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_KTOR_CLIENT_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-ktor-client",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
