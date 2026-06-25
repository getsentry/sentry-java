import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
}

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
}

dependencies {
  api(projects.sentry)

  compileOnly(libs.context.propagation)
  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.reactor.core)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.sentryTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.context.propagation)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.reactor.core)
  testImplementation(platform("org.junit:junit-bom:5.10.0"))
  testImplementation("org.junit.jupiter:junit-jupiter")
}

buildConfig {
  useJavaOutput()
  packageName("io.sentry.reactor")
  buildConfigField(
    "String",
    "SENTRY_REACTOR_SDK_NAME",
    "\"${Config.Sentry.SENTRY_REACTOR_SDK_NAME}\"",
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

repositories { mavenCentral() }

tasks.jar {
  manifest {
    attributes(
      "Sentry-Version-Name" to project.version,
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_REACTOR_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-reactor",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
