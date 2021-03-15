apply(plugin = "maven-publish")
apply(plugin = "signing")

var signingEnabled: Boolean = isSigningEnabled()

// add sources and javadocs jar to non-android modules
if (project.plugins.hasPlugin("java")) {
    configure<JavaPluginExtension> {
        withSourcesJar()
        withJavadocJar()
    }
}

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

        if (signingEnabled) {
            configure<SigningExtension> {
                sign(publications["maven"])
            }
        }
    }
}

/**
 * If package signing is enabled. Default: false.
 */
fun isSigningEnabled(): Boolean {
    return if (project.hasProperty("signingEnabled")) {
        with(property("signingEnabled") as String) {
            this.toBoolean()
        }
    } else {
        false
    }
}

fun isAndroid() = project.name.contains("android")
fun componentName() = if (isAndroid()) "release" else "java"
fun packaging() = if (isAndroid()) "aar" else "jar"
