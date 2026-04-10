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

application { mainClass.set("io.sentry.samples.netflix.dgs.NetlixDgsApplication") }

group = "io.sentry.sample.spring-boot"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_1_8

java.targetCompatibility = JavaVersion.VERSION_1_8

repositories { mavenCentral() }

dependencies {
  implementation(platform(libs.springboot2.bom))
  implementation(libs.springboot.starter.web)
  implementation(Config.Libs.kotlinReflect)
  implementation(kotlin(Config.kotlinStdLib, KotlinCompilerVersion.VERSION))
  implementation(projects.sentrySpringBootStarter)
  implementation(projects.sentryGraphql)
  implementation(platform("com.netflix.graphql.dgs:graphql-dgs-platform-dependencies:4.9.2"))
  implementation("com.netflix.graphql.dgs:graphql-dgs-subscriptions-websockets-autoconfigure:4.9.2")
  implementation("com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter")
  testImplementation(libs.springboot.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
}

// Shadow 9.x enforces DuplicatesStrategy before transformers run, so the `append`
// transformer only sees one copy of each file. We pre-merge Spring metadata files
// from the runtime classpath and include the merged result in the shadow JAR.
val mergeSpringMetadata by
  tasks.registering {
    val outputDir = project.layout.buildDirectory.dir("merged-spring-metadata/META-INF")
    val filesToMerge =
      listOf(
        "spring.factories",
        "spring.handlers",
        "spring.schemas",
        "spring-autoconfigure-metadata.properties",
      )
    outputs.dir(outputDir)
    inputs.files(configurations.runtimeClasspath)
    doLast {
      val out = outputDir.get().asFile
      out.mkdirs()
      filesToMerge.forEach { fileName ->
        val merged = StringBuilder()
        configurations.runtimeClasspath
          .get()
          .filter { it.name.endsWith(".jar") }
          .forEach { jar ->
            try {
              val zip = ZipFile(jar)
              val entry = zip.getEntry("META-INF/$fileName")
              if (entry != null) {
                merged.append(zip.getInputStream(entry).bufferedReader().readText())
                if (!merged.endsWith("\n")) merged.append("\n")
              }
              zip.close()
            } catch (e: Exception) {
              /* skip non-zip files */
            }
          }
        if (merged.isNotEmpty()) {
          File(out, fileName).writeText(merged.toString())
        }
      }
    }
  }

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  dependsOn(mergeSpringMetadata)
  manifest { attributes["Main-Class"] = "io.sentry.samples.netflix.dgs.NetlixDgsApplication" }
  archiveClassifier.set("")
  from(mergeSpringMetadata.map { project.layout.buildDirectory.dir("merged-spring-metadata") }) {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
  }
  mergeServiceFiles()
}

tasks.jar {
  enabled = false
  dependsOn(tasks.shadowJar)
}

tasks.startScripts { dependsOn(tasks.shadowJar) }

tasks.withType<Test>().configureEach { useJUnitPlatform() }

tasks.withType<KotlinCompile>().configureEach {
  kotlin {
    compilerOptions.freeCompilerArgs = listOf("-Xjsr305=strict")
    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
  }
}
