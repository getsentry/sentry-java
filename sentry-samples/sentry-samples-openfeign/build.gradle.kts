plugins {
    java
    application
    id(Config.QualityPlugins.gradleVersions)
}

application {
    mainClass.set("io.sentry.samples.openfeign.Main")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":sentry"))
    implementation(project(":sentry-openfeign"))
    implementation(Config.Libs.feignCore)
    implementation(Config.Libs.feignGson)
}
