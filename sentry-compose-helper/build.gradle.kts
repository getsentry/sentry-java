import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    jacoco
    id("org.jetbrains.compose")
    id(Config.QualityPlugins.gradleVersions)
    id(Config.BuildPlugins.buildConfig) version Config.BuildPlugins.buildConfigVersion
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

dependencies {
    implementation(projects.sentry)
    implementation(compose.runtime)
    implementation(compose.ui)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

val embeddedJar by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add("embeddedJar", File("$buildDir/libs/sentry-compose-helper-$version.jar"))
}
