

plugins {
    java
    application
    id(Config.QualityPlugins.gradleVersions)
}

application {
    mainClass.set("io.sentry.samples.jul.Main")
    applicationDefaultJvmArgs = mutableListOf("-Djava.util.logging.config.file=${project.projectDir}/src/main/resources/logging.properties")
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(projects.sentryJul)
    implementation(Config.Libs.logbackClassic)
}
