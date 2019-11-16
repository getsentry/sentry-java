import com.novoda.gradle.release.PublishExtension

plugins {
    id("com.android.library")
    kotlin("android")
    maven
    id(Config.Deploy.novodaBintrayId)
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

//TODO: move thse blocks to parent gradle file, DRY
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
    dryRun = Config.Deploy.dryRun
    override = Config.Deploy.override
    // TODO: uncomment it to publish new version, waiting PR to be merged
//    sign = Config.Deploy.sign
    artifactId = "sentry-android"
}

gradle.taskGraph.whenReady {
    allTasks.find {
        it.path == ":${project.name}:generatePomFileForReleasePublication"
    }?.doLast {
        println("delete file: " + file("build/publications/release/pom-default.xml").delete())
        println("Overriding pom-file to make sure we can sync to maven central!")

        maven.pom {
            withGroovyBuilder {
                "project" {
                    "name"(project.name)
                    "artifactId"("sentry-android")
                    "packaging"("aar")
                    "description"(Config.Sentry.description)
                    "url"(Config.Sentry.website)
                    "version"(project.version.toString())

                    "scm" {
                        "url"(Config.Sentry.repository)
                        "connection"(Config.Sentry.repository)
                        "developerConnection"(Config.Sentry.repository)
                    }

                    "licenses" {
                        "license" {
                            "name"(Config.Sentry.licence)
                        }
                    }

                    "developers" {
                        "developer" {
                            "id"(Config.Sentry.devUser)
                            "name"(Config.Sentry.devName)
                            "email"(Config.Sentry.devEmail)
                        }
                    }
                }
            }
        }.writeTo("build/publications/release/pom-default.xml")
    }
}
