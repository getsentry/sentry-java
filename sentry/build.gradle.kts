plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
    id(Config.BuildPlugins.buildConfig) version Config.BuildPlugins.buildConfigVersion
    `maven-publish`
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
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
    testImplementation(Config.TestLibs.mockitoInline)
    testImplementation(Config.TestLibs.awaitility)
    testImplementation(project(":sentry-test-support"))
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

jacoco {
    toolVersion = Config.QualityPlugins.Jacoco.version
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
            rule { limit { minimum = Config.QualityPlugins.Jacoco.minimumCoverage } }
        }
    }
    check {
        dependsOn(jacocoTestCoverageVerification)
        dependsOn(jacocoTestReport)
    }
    test {
        environment["SENTRY_TEST_PROPERTY"] = "\"some-value\""
        environment["SENTRY_TEST_MAP_KEY1"] = "\"value1\""
        environment["SENTRY_TEST_MAP_KEY2"] = "value2"
    }
}

buildConfig {
    useJavaOutput()
    packageName("io.sentry")
    buildConfigField("String", "SENTRY_JAVA_SDK_NAME", "\"${Config.Sentry.SENTRY_JAVA_SDK_NAME}\"")
    buildConfigField("String", "VERSION_NAME", "\"${project.version}\"")
}

val generateBuildConfig by tasks
tasks.withType<JavaCompile>().configureEach {
    dependsOn(generateBuildConfig)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
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
