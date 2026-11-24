plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.errorprone) apply false
  alias(libs.plugins.detekt) apply false
  alias(libs.plugins.gradle.versions) apply false
  alias(libs.plugins.sentry) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.sqldelight) apply false
}
