import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.zip.ZipFile
import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  application
  alias(libs.plugins.shadow)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

application { mainClass.set("io.sentry.samples.spring.boot.SentryDemoApplication") }

group = "io.sentry.sample.spring-boot"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_17

java.targetCompatibility = JavaVersion.VERSION_17

repositories { mavenCentral() }

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
  }
}

dependencies {
  implementation(platform(libs.springboot2.bom))
  implementation(libs.springboot.starter)
  implementation(libs.springboot.starter.actuator)
  implementation(libs.springboot.starter.aop)
  implementation(libs.springboot.starter.graphql)
  implementation(libs.springboot.starter.jdbc)
  implementation(libs.springboot.starter.quartz)
  implementation(libs.springboot.starter.security)
  implementation(libs.springboot.starter.web)
  implementation(libs.springboot.starter.webflux)
  implementation(libs.springboot.starter.websocket)
  implementation(Config.Libs.aspectj)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpringBootStarter)
  implementation(projects.sentryLogback)
  implementation(projects.sentryGraphql)
  implementation(projects.sentryQuartz)
  implementation(projects.sentryAsyncProfiler)
  implementation(libs.otel)

  // database query tracing
  implementation(projects.sentryJdbc)
  runtimeOnly(libs.hsqldb)

  testImplementation(kotlin(Config.kotlinStdLib))
  testImplementation(projects.sentrySystemTestSupport)
  testImplementation(libs.apollo3.kotlin)
  testImplementation(libs.kotlin.test.junit)
  testImplementation(libs.slf4j2.api)
  testImplementation(libs.springboot.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
  testImplementation("ch.qos.logback:logback-classic:1.5.16")
  testImplementation("ch.qos.logback:logback-core:1.5.16")
  testImplementation("org.apache.httpcomponents:httpclient")
}

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.sentry.samples.spring.boot.SentryDemoApplication" }
  archiveClassifier.set("")
  mergeServiceFiles()

  val springMetadataFiles =
    listOf(
      "META-INF/spring.factories",
      "META-INF/spring.handlers",
      "META-INF/spring.schemas",
      "META-INF/spring-autoconfigure-metadata.properties",
      "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports",
      "META-INF/spring/org.springframework.boot.actuate.autoconfigure.web.ManagementContextConfiguration.imports",
    )

  doLast {
    val jar = archiveFile.get().asFile
    val runtimeJars = project.configurations.getByName("runtimeClasspath").resolve().filter { it.name.endsWith(".jar") }
    val uri = URI.create("jar:${jar.toURI()}")
    FileSystems.newFileSystem(uri, mapOf("create" to "false")).use { fs ->
      springMetadataFiles.forEach { entryPath ->
        val merged = StringBuilder()
        runtimeJars.forEach { depJar ->
          try {
            val zip = ZipFile(depJar)
            val entry = zip.getEntry(entryPath)
            if (entry != null) {
              merged.append(zip.getInputStream(entry).bufferedReader().readText())
              if (!merged.endsWith("\n")) merged.append("\n")
            }
            zip.close()
          } catch (e: Exception) { /* skip non-zip files */ }
        }
        if (merged.isNotEmpty()) {
          val target = fs.getPath(entryPath)
          if (target.parent != null) Files.createDirectories(target.parent)
          Files.write(target, merged.toString().toByteArray())
        }
      }
    }
  }
}

tasks.jar {
  enabled = false
  dependsOn(tasks.shadowJar)
}

tasks.startScripts { dependsOn(tasks.shadowJar) }

configure<SourceSetContainer> { test { java.srcDir("src/test/java") } }

tasks.register<JavaExec>("bootRunWithAgent").configure {
  group = "application"

  mainClass.set("io.sentry.samples.spring.boot.SentryDemoApplication")
  classpath = sourceSets["main"].runtimeClasspath

  val versionName = project.properties["versionName"] as String
  val agentJarPath =
    "$rootDir/sentry-opentelemetry/sentry-opentelemetry-agent/build/libs/sentry-opentelemetry-agent-$versionName.jar"

  val dsn =
    System.getenv("SENTRY_DSN")
      ?: "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563"
  val tracesSampleRate = System.getenv("SENTRY_TRACES_SAMPLE_RATE") ?: "1"

  environment("SENTRY_DSN", dsn)
  environment("SENTRY_TRACES_SAMPLE_RATE", tracesSampleRate)
  environment("OTEL_TRACES_EXPORTER", "none")
  environment("OTEL_METRICS_EXPORTER", "none")
  environment("OTEL_LOGS_EXPORTER", "none")

  jvmArgs = listOf("-Dotel.javaagent.debug=true", "-javaagent:$agentJarPath")
}

tasks.register<Test>("systemTest").configure {
  group = "verification"
  description = "Runs the System tests"

  val test = project.extensions.getByType<SourceSetContainer>()["test"]
  testClassesDirs = test.output.classesDirs
  classpath = test.runtimeClasspath

  outputs.upToDateWhen { false }

  maxParallelForks = 1

  // Cap JVM args per test
  minHeapSize = "128m"
  maxHeapSize = "1g"

  filter { includeTestsMatching("io.sentry.systemtest*") }
}

tasks.named("test").configure {
  require(this is Test)

  filter { excludeTestsMatching("io.sentry.systemtest.*") }
}
