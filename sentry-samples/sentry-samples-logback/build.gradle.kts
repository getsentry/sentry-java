plugins {
    java
    application
    id(Config.QualityPlugins.gradleVersions)
}

application {
    mainClass.set("io.sentry.samples.logback.Main")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
//    implementation(projects.sentryLogback)
    implementation("io.sentry:sentry-logback:6.18.1")
    implementation(Config.Libs.logbackClassic)
    implementation("org.apache.kafka:kafka-clients:3.4.0")
}
