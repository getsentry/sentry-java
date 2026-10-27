import io.sentry.gradle.MergeSpringMetadataAction
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  java
  application
  alias(libs.plugins.shadow)
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.spring)
}

val sentryVersion = libs.versions.sentry.get()

application { mainClass.set("io.sentry.samples.netflix.dgs.NetlixDgsApplication") }

group = "io.sentry.sample.spring-boot"

version = "0.0.1-SNAPSHOT"

java.sourceCompatibility = JavaVersion.VERSION_1_8

java.targetCompatibility = JavaVersion.VERSION_1_8

dependencies {
  implementation(platform(libs.springboot2.bom))
  implementation(libs.springboot.starter.web)
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(kotlin("stdlib-jdk8", getKotlinPluginVersion()))
  implementation("io.sentry:sentry-spring-boot-starter:$sentryVersion")
  implementation("io.sentry:sentry-graphql:$sentryVersion")
  implementation(platform("com.netflix.graphql.dgs:graphql-dgs-platform-dependencies:4.9.2"))
  implementation("com.netflix.graphql.dgs:graphql-dgs-subscriptions-websockets-autoconfigure:4.9.2")
  implementation("com.netflix.graphql.dgs:graphql-dgs-spring-boot-starter")
  testImplementation(libs.springboot.starter.test) {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
}

val runtimeClasspath = configurations.named("runtimeClasspath")

// Configure the Shadow JAR (executable JAR with all dependencies)
tasks.shadowJar {
  manifest { attributes["Main-Class"] = "io.sentry.samples.netflix.dgs.NetlixDgsApplication" }
  archiveClassifier.set("")

  doLast(
    MergeSpringMetadataAction(
      runtimeClasspath.get(),
      MergeSpringMetadataAction.DEFAULT_SPRING_METADATA_FILES,
    )
  )
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
