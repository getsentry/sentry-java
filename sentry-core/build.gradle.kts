import com.novoda.gradle.release.PublishExtension

plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    id(Config.QualityPlugins.errorProne)
    id(Config.Deploy.novodaBintray)
    id(Config.QualityPlugins.gradleVersions)
}

dependencies {
    // Envelopes require JSON. Until a parse is done without GSON, we'll depend on it explicitly here
    implementation(Config.Libs.gson)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorproneJavac(Config.CompileOnly.errorProneJavac8)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

jacoco {
    toolVersion = Config.QualityPlugins.jacocoVersion
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        html.isEnabled = false
    }
}

tasks {
    jacocoTestCoverageVerification {
        violationRules {
            // TODO: Raise the minimum to a sensible value.
            rule { limit { minimum = BigDecimal.valueOf(0.1) } }
        }
    }
    check {
        dependsOn(jacocoTestCoverageVerification)
        dependsOn(jacocoTestReport)
    }
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
