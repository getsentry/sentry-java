import com.novoda.gradle.release.PublishExtension

plugins {
    id("com.android.library")
    kotlin("android")
    id(Config.Deploy.novodaBintray)
    id(Config.QualityPlugins.gradleVersions)
}

android {
    compileSdkVersion(Config.Android.compileSdkVersion)
    buildToolsVersion(Config.Android.buildToolsVersion)

    defaultConfig {
        targetSdkVersion(Config.Android.targetSdkVersion)
        minSdkVersion(Config.Android.minSdkVersionNdk)

        versionName = project.version.toString()
        versionCode = Config.Sentry.buildVersionCode
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // replace with https://issuetracker.google.com/issues/72050365 once released.
    libraryVariants.all {
        generateBuildConfigProvider?.configure {
            enabled = false
        }
    }
}

dependencies {
    api(project(":sentry-android-core"))
    api(project(":sentry-android-ndk"))
}

//TODO: move these blocks to parent gradle file, DRY
configure<PublishExtension> {
    userOrg = Config.Sentry.userOrg
    groupId = project.group.toString()
    publishVersion = project.version.toString()
    desc = Config.Sentry.description
    website = Config.Sentry.website
    repoName = Config.Sentry.repoName
    setLicences(Config.Sentry.licence)
    issueTracker = Config.Sentry.issueTracker
    repository = Config.Sentry.repository
    sign = Config.Deploy.sign
    mavenCentralSync = Config.Deploy.mavenCentralSync
    artifactId = project.name
}

afterEvaluate {
    (publishing.publications.all {
        (this as MavenPublication).apply {
            pom {
                licenses {
                    license {
                        name.set(Config.Sentry.licence)
                        url.set(Config.Sentry.licenceUrl)
                    }
                }
                developers {
                    developer {
                        id.set(Config.Sentry.userOrg)
                        name.set(Config.Sentry.devName)
                        email.set(Config.Sentry.devEmail)
                    }
                }
                scm {
                    connection.set(Config.Sentry.scmConnection)
                    developerConnection.set(Config.Sentry.scmDevConnection)
                    url.set(Config.Sentry.scmUrl)
                }
            }
        }
    })
}
