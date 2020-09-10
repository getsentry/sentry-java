plugins {
    java
    id(Config.QualityPlugins.gradleVersions)
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":sentry-logback"))
    implementation(Config.Libs.logbackClassic)
}
