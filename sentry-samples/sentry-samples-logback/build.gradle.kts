plugins {
    java
    application
    id(Config.QualityPlugins.gradleVersions)
}

application {
    mainClassName = "io.sentry.samples.logback.Main"
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":sentry-logback"))
    implementation(Config.Libs.logbackClassic)
}
