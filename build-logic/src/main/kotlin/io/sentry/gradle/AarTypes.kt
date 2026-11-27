package io.sentry.gradle

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import io.sentry.vendor.androidx.maven.assignAarTypes

private val androidLibs =
  setOf(
    "sentry-android-core",
    "sentry-android-ndk",
    "sentry-android-ndk-outproc",
    "sentry-android-fragment",
    "sentry-android-navigation",
    "sentry-android-navigation3",
    "sentry-android-timber",
    "sentry-compose-android",
    "sentry-android-sqlite",
    "sentry-android-replay",
  )

private val androidXLibs = listOf("androidx.core:core")

// Workaround for https://github.com/gradle/gradle/issues/3170
fun MavenPublishBaseExtension.assignAarTypes() {
  pom { withXml { asNode().assignAarTypes(androidLibs, androidXLibs) } }
}
