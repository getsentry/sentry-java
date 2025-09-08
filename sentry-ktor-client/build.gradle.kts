import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  alias(libs.plugins.kotlin.jvm)
  jacoco
  id("io.sentry.javadoc")
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  alias(libs.plugins.animalsniffer)
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
}

kotlin { explicitApi() }

dependencies {
  api(projects.sentry)

  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  api(projects.sentryKotlinExtensions)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.ktor.client.core)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  testImplementation(projects.sentryTestSupport)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.mockito.inline)
  testImplementation(libs.ktor.client.core)
  testImplementation(libs.ktor.client.java)
  testImplementation(libs.okhttp.mockwebserver)

  val gummyBearsModule = libs.gummy.bears.api21.get().module
  signature("${gummyBearsModule}:${libs.versions.gummyBears.get()}:coreLib2@signature")
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
    dependsOn(animalsnifferMain)
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
