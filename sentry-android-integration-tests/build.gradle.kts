// Loads the plugins once for every subproject, so they all share one classpath.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.errorprone) apply false
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.gradle.versions) apply false
  alias(libs.plugins.sentry) apply false
}
