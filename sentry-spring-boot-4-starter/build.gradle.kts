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
  alias(libs.plugins.springboot4) apply false
}

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
  kotlinOptions.jvmTarget = JavaVersion.VERSION_17.toString()
  kotlinOptions.languageVersion = libs.versions.kotlin.compatible.version.get()
}

dependencies {
  api(projects.sentrySpringBoot4)
  api(libs.springboot4.starter)

  annotationProcessor(platform(SpringBootPlugin.BOM_COORDINATES))
  annotationProcessor(Config.AnnotationProcessors.springBootAutoConfigure)
  annotationProcessor(Config.AnnotationProcessors.springBootConfiguration)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)

  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)
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

tasks.jar {
  manifest {
    attributes(
      "Sentry-Version-Name" to project.version,
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_SPRING_BOOT_4_STARTER_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-spring-boot-4-starter",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
