import io.spring.gradle.dependencymanagement.dsl.DependencyManagementExtension

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    `java-library`
    kotlin("jvm")
    id("org.hibernate.jakarta-transformer") version "0.9.6"
    id(Config.BuildPlugins.springBoot) version Config.springBootVersion apply false
    id(Config.BuildPlugins.springDependencyManagement) version Config.BuildPlugins.springDependencyManagementVersion
}

the<DependencyManagementExtension>().apply {
    imports {
        mavenBom(SpringBootPlugin.BOM_COORDINATES)
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
    kotlinOptions.languageVersion = Config.kotlinCompatibleLanguageVersion
}

dependencies {
    // Specifying the transformer's dependencies is optional.  0.2.0 is used by default
    jakartaTransformerTool(
        "org.eclipse.transformer:org.eclipse.transformer:0.2.0",
    )
    jakartaTransformerTool("org.eclipse.transformer:org.eclipse.transformer.cli:0.2.0")
}

jakartaTransformation {
    shadow(projects.sentrySpring) {
        withSources()
        withJavadoc()
    }
}

