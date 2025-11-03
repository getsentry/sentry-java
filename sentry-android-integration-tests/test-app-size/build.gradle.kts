plugins {
  id("com.android.application")
  id("io.sentry.android.gradle")
}

android {
  namespace = "io.sentry.tests.size"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "io.sentry.tests.size"
    minSdk = libs.versions.minSdk.get().toInt()
    targetSdk = libs.versions.targetSdk.get().toInt()
    versionCode = 1
    versionName = project.version.toString()
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

sentry {
  org.set("sentry-sdks")
  projectName.set("sentry-android")
  authToken.set(System.getenv("SENTRY_AUTH_TOKEN"))
  autoUploadProguardMapping.set(true)
  tracingInstrumentation.enabled.set(false)
  includeDependenciesReport.set(false)
  telemetry.set(false)
  sizeAnalysis.enabled.set(true)
}

configurations.configureEach {
  exclude(group = "org.jetbrains.kotlin")
  exclude(group = "androidx.core")
  exclude(group = "androidx.lifecycle")
}