apply(plugin = "maven-publish")
apply(plugin = "signing")

afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("maven") {
                from(components[componentName()])
                pom {
                    name.set("${project.group}:${project.name}")
                    description.set(Config.Sentry.description)
                    url.set(Config.Sentry.repository)
                    packaging = packaging()
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

//        configure<SigningExtension> {
//            sign(publications["maven"])
//        }
    }
}


fun componentName() = if (project.name.contains("android")) "release" else "java"
fun packaging() = if (project.name.contains("android")) "aar" else "jar"

