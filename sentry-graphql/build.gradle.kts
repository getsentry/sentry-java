import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.sentry.javadoc")
  kotlin("jvm")
  jacoco
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
  kotlinOptions.languageVersion = libs.versions.kotlin.compatible.version.get()
}

dependencies {
  api(projects.sentry)
  api(projects.sentryGraphqlCore)

  compileOnly(libs.graphql.java17)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.sentry)
  testImplementation(projects.sentryTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.graphql.java17)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
  testImplementation(libs.okhttp)
  testImplementation(libs.okhttp.mockwebserver)
  testImplementation(libs.springboot.starter.graphql)
  testImplementation("com.netflix.graphql.dgs:graphql-error-types:4.9.2")
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

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.sentry")
  }
}

buildConfig {
  useJavaOutput()
  packageName("io.sentry.graphql")
  buildConfigField(
    "String",
    "SENTRY_GRAPHQL_SDK_NAME",
    "\"${Config.Sentry.SENTRY_GRAPHQL_SDK_NAME}\"",
  )
  buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.jar {
  manifest {
    attributes(
      "Sentry-Version-Name" to project.version,
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_GRAPHQL_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-graphql",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
