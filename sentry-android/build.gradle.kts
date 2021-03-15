plugins {
    id("com.android.library")
    kotlin("android")
    id(Config.QualityPlugins.gradleVersions)
}

apply(from = "$rootDir/gradle/publishing.gradle.kts")

android {
    compileSdkVersion(Config.Android.compileSdkVersion)

    defaultConfig {
        targetSdkVersion(Config.Android.targetSdkVersion)
        minSdkVersion(Config.Android.minSdkVersionNdk)

        versionName = project.version.toString()
        versionCode = project.properties[Config.Sentry.buildVersionCodeProp].toString().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        // Determines whether to generate a BuildConfig class.
        buildConfig = false
    }
}

tasks {
    val androidJavadocs by creating(Javadoc::class) {
        source = android.sourceSets["main"].java.getSourceFiles()
    }

    val androidJavadocsJar by creating(Jar::class) {
        archiveClassifier.set("javadoc")
        from(androidJavadocs.destinationDir)
    }

    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(android.sourceSets["main"].java.srcDirs)
    }

    artifacts {
        archives(sourcesJar)
        archives(androidJavadocsJar)
    }
}

dependencies {
    api(project(":sentry-android-core"))
    api(project(":sentry-android-ndk"))
}
