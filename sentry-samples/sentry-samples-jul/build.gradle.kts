

plugins {
    java
    application
    id(Config.QualityPlugins.gradleVersions)
}

application {
    mainClassName = "io.sentry.samples.jul.Main"
    applicationDefaultJvmArgs = mutableListOf("-Djava.util.logging.config.file=${project.projectDir}/src/main/resources/logging.properties")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation(project(":sentry-jul"))
    implementation(Config.Libs.logbackClassic)
}
