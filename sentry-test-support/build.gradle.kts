plugins {
    `java-library`
    kotlin("jvm")
    jacoco
    id(Config.QualityPlugins.errorProne)
    id(Config.QualityPlugins.gradleVersions)
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = JavaVersion.VERSION_1_8.toString()
}

dependencies {
    api(project(":sentry"))
    // Envelopes require JSON. Until a parse is done without GSON, we'll depend on it explicitly here
    implementation(Config.Libs.gson)

    compileOnly(Config.CompileOnly.nopen)
    errorprone(Config.CompileOnly.nopenChecker)
    errorprone(Config.CompileOnly.errorprone)
    errorproneJavac(Config.CompileOnly.errorProneJavac8)
    compileOnly(Config.CompileOnly.jetbrainsAnnotations)

    // tests
    implementation(kotlin(Config.kotlinStdLib))
    implementation(Config.TestLibs.kotlinTestJunit)
    implementation(Config.TestLibs.mockitoKotlin)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}
