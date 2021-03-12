apply(plugin = "maven-publish")

configure<PublishingExtension> {
    afterEvaluate {
        publications {
            create<MavenPublication>("maven") {
                from(components[componentName()])
                pom {
                    description.set(Config.Sentry.description)
                    url.set(Config.Sentry.website)
                    packaging = "jar"
                    licenses {
                        license {
                            name.set(Config.Sentry.licence)
                            url.set(Config.Sentry.licenceUrl)
                        }
                    }
                    developers {
                        developer {
                            id.set(Config.Sentry.userOrg)
                            email.set(Config.Sentry.devEmail)
                            name.set(Config.Sentry.devName)
                        }
                    }
                    scm {
                        connection.set(Config.Sentry.scmConnection)
                        developerConnection.set(Config.Sentry.scmDevConnection)
                        url.set(Config.Sentry.scmUrl)
                    }
                    issueManagement {
                        url.set(Config.Sentry.issueTracker)
                    }
                }
                groupId = project.group.toString()
                artifactId = project.name
            }
        }
    }
}

fun componentName() = if (project.name.contains("android")) "release" else "java"

