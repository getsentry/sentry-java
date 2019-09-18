plugins {
    `kotlin-dsl`
}

repositories {
    jcenter()
}

// Disabling the warning about the use of experimental Kotlin compiler features
kotlinDslPluginOptions {
    experimentalWarning.set(false)
}
