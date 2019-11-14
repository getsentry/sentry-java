import com.novoda.gradle.release.PublishExtension

plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    id("net.ltgt.errorprone")
    maven
    id(Config.Deploy.novodaBintrayId)
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
    dryRun = Config.Deploy.dryRun
    override = Config.Deploy.override
    // TODO: uncomment it to publish new version, waiting PR to be merged
//    sign = Config.Deploy.sign
    artifactId = "sentry-core"
}

gradle.taskGraph.whenReady {
    allTasks.find {
        it.path == ":${project.name}:generatePomFileForMavenPublication"
    }?.doLast {
        println("delete file: " + file("build/publications/maven/pom-default.xml").delete())
        println("Overriding pom-file to make sure we can sync to maven central!")

        maven.pom {
            withGroovyBuilder {
                "project" {
                    "name"(project.name)
                    "artifactId"("sentry-core")
                    "packaging"("jar")
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
        }.writeTo("build/publications/maven/pom-default.xml")
    }
}
