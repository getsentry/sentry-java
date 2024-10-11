plugins {
    java
    application
    id(Config.QualityPlugins.gradleVersions)
}

application {
    mainClass.set("io.sentry.samples.log4j2.Main")
}

configure<JavaPluginExtension> {
    // https://docs.gradle.org/current/userguide/java_plugin.html#packaging
    withJavadocJar()
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(projects.sentryLog4j2)
    implementation(Config.Libs.log4j2Api)
}
