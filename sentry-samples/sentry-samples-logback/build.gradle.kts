plugins {
    java
    application
    id(Config.QualityPlugins.gradleVersions)
}

application {
    mainClass.set("io.sentry.samples.logback.Main")
}

configure<JavaPluginExtension> {
    // https://docs.gradle.org/current/userguide/java_plugin.html#packaging
    withJavadocJar()
    withSourcesJar()

    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(projects.sentryLogback)
    implementation(Config.Libs.logbackClassic)
}
