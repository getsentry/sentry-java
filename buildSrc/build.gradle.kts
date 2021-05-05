plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

// Disabling the warning about the use of experimental Kotlin compiler features
kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
