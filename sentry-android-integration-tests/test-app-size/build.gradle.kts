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
      ndk {
        abiFilters.clear()
        abiFilters.add("arm64-v8a")
      }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  androidComponents.beforeVariants {
    it.enable = !Config.Android.shouldSkipDebugVariant(it.buildType)
  }
}

configurations.configureEach {
  exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
  exclude(group = "androidx.core")
  exclude(group = "androidx.lifecycle")
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
