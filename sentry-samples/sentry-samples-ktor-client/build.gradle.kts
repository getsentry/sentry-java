plugins {
  alias(libs.plugins.kotlin.jvm)
  application
  alias(libs.plugins.gradle.versions)
}

application { mainClass.set("io.sentry.samples.ktorClient.Main") }

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
}

dependencies {
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.java)
  implementation(libs.kotlinx.coroutines)
  implementation(projects.sentry)
  implementation(projects.sentryKtorClient)
}

tasks.test { useJUnitPlatform() }
