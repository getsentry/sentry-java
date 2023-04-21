import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    google()
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

dependencies {
    implementation("com.vanniktech:gradle-maven-publish-plugin:0.18.0")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:1.6.10")
    implementation("com.android.tools.build:gradle:7.3.0")
}
