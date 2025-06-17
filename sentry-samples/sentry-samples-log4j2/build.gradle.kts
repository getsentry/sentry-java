plugins {
    java
    application
    alias(libs.plugins.gradle.versions)
}

application {
    mainClass.set("io.sentry.samples.log4j2.Main")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(projects.sentryLog4j2)
    implementation(libs.log4j.api)
}
