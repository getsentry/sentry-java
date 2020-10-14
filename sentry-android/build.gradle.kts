import com.novoda.gradle.release.PublishExtension

plugins {
    id("com.android.library")
    kotlin("android")
    id(Config.Deploy.novodaBintray)
    id(Config.QualityPlugins.gradleVersions)
    distribution
}

// overwrite distributions config from the root project
distributions {
    main {
        contents {
            from("build/outputs/aar")
            from("build/publications/release")
        }
    }
}

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

dependencies {
    api(project(":sentry-android-core"))
    api(project(":sentry-android-ndk"))
}

// TODO: move these blocks to parent gradle file, DRY
configure<PublishExtension> {
    userOrg = Config.Sentry.userOrg
    groupId = project.group.toString()
    publishVersion = project.version.toString()
    desc = Config.Sentry.description
    website = Config.Sentry.website
    repoName = Config.Sentry.androidRepoName
    setLicences(Config.Sentry.licence)
    setLicenceUrls(Config.Sentry.licenceUrl)
    issueTracker = Config.Sentry.issueTracker
    repository = Config.Sentry.repository
    sign = Config.Deploy.sign
    artifactId = project.name
    uploadName = "${project.group}:${project.name}"
    devId = Config.Sentry.userOrg
    devName = Config.Sentry.devName
    devEmail = Config.Sentry.devEmail
    scmConnection = Config.Sentry.scmConnection
    scmDevConnection = Config.Sentry.scmDevConnection
    scmUrl = Config.Sentry.scmUrl
}
