plugins {
  java
  application
  alias(libs.plugins.gradle.versions)
}

application { mainClass.set("io.sentry.samples.console.Main") }

configure<JavaPluginExtension> {
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies { implementation(projects.sentry) }
