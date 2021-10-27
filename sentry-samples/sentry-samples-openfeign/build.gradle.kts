plugins {
    java
    application
    id(Config.QualityPlugins.gradleVersions)
}

application {
    mainClass.set("io.sentry.samples.openfeign.Main")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(projects.sentry)
    implementation(projects.sentryOpenfeign)
    implementation(Config.Libs.feignCore)
    implementation(Config.Libs.feignGson)
}
