import net.ltgt.gradle.errorprone.errorprone
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `java-library`
  id("io.sentry.javadoc")
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.errorprone)
  alias(libs.plugins.gradle.versions)
  alias(libs.plugins.buildconfig)
  id("io.sentry.animalsniffer")
  id("io.sentry.spotless")
  id("io.sentry.jdk-target")
  id("io.sentry.publish")
  id("io.sentry.api-validation")
}

tasks.named<KotlinCompile>("compileKotlin") {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
  compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
  compilerOptions.apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_1_9
}

dependencies {
  api(projects.sentry)

  compileOnly(libs.jetbrains.annotations)
  compileOnly(libs.nopen.annotations)
  compileOnly(libs.servlet.api)
  errorprone(libs.errorprone.core)
  errorprone(libs.nopen.checker)
  errorprone(libs.nullaway)

  // tests
  testImplementation(projects.sentryTestSupport)
  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(libs.awaitility.kotlin)
  testImplementation(libs.google.truth)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.mockito.kotlin)
  testImplementation(libs.springboot.starter.test)
  testImplementation(libs.springboot.starter.web)
}

testing {
  suites {
    register<JvmTestSuite>("integrationTest") {
      useJUnit()
      dependencies {
        implementation(project())
        implementation(projects.sentryTestSupport)
        implementation(libs.google.truth)
        implementation(libs.kotlin.test.junit)
        implementation(libs.tomcat85.embed.core)
      }
      targets.all {
        testTask.configure {
          description = "Runs the servlet integration test on Tomcat 8.5 and Servlet 3.1"
          systemProperty("io.sentry.test.tomcat-version-prefix", "8.5.")
          systemProperty("io.sentry.test.servlet-major-version", "3")
          systemProperty("io.sentry.test.servlet-minor-version", "1")
        }
      }
    }

    register<JvmTestSuite>("integrationTestTomcat9") {
      useJUnit()
      dependencies {
        implementation(project())
        implementation(projects.sentryTestSupport)
        implementation(libs.google.truth)
        implementation(libs.kotlin.test.junit)
        implementation(libs.tomcat9.embed.core)
      }
      sources {
        java.setSrcDirs(sourceSets["integrationTest"].allJava.srcDirs)
        resources.setSrcDirs(sourceSets["integrationTest"].resources.srcDirs)
      }
      targets.all {
        testTask.configure {
          description = "Runs the servlet integration test on Tomcat 9 and Servlet 4"
          systemProperty("io.sentry.test.tomcat-version-prefix", "9.0.")
          systemProperty("io.sentry.test.servlet-major-version", "4")
          systemProperty("io.sentry.test.servlet-minor-version", "0")
        }
      }
    }
  }
}

tasks.check {
  dependsOn(
    testing.suites.named("integrationTest"),
    testing.suites.named("integrationTestTomcat9"),
  )
}

tasks.withType<JavaCompile>().configureEach {
  options.errorprone {
    check("NullAway", net.ltgt.gradle.errorprone.CheckSeverity.ERROR)
    option("NullAway:AnnotatedPackages", "io.sentry")
  }
}

buildConfig {
  useJavaOutput()
  packageName("io.sentry.servlet")
  buildConfigField(
    "String",
    "SENTRY_SERVLET_SDK_NAME",
    "\"${Config.Sentry.SENTRY_SERVLET_SDK_NAME}\"",
  )
  buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

tasks.jar {
  manifest {
    attributes(
      "Sentry-Version-Name" to project.version,
      "Sentry-SDK-Name" to Config.Sentry.SENTRY_SERVLET_SDK_NAME,
      "Sentry-SDK-Package-Name" to "maven:io.sentry:sentry-servlet",
      "Implementation-Vendor" to "Sentry",
      "Implementation-Title" to project.name,
      "Implementation-Version" to project.version,
    )
  }
}
