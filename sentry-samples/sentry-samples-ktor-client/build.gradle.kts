plugins {
  alias(libs.plugins.kotlin.jvm)
  application
  alias(libs.plugins.gradle.versions)
}

val sentryVersion = libs.versions.sentry.get()

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
  implementation("io.sentry:sentry:$sentryVersion")
  implementation("io.sentry:sentry-ktor-client:$sentryVersion")
}

tasks.test { useJUnitPlatform() }
