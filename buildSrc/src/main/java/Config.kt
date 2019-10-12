object Config {
    val kotlinVersion = "1.3.50"
    val kotlinStdLib = "stdlib-jdk8"

    object BuildPlugins {
        val androidGradle = "com.android.tools.build:gradle:3.5.1"
        val kotlinGradlePlugin = "gradle-plugin"
    }

    object Android {
        private val sdkVersion = 29

        val buildToolsVersion = "29.0.2"
        val minSdkVersion = 14
        val minSdkVersionDebug = 21
        val targetSdkVersion = sdkVersion
        val compileSdkVersion = sdkVersion
    }

    object Libs {
        val appCompat = "androidx.appcompat:appcompat:1.1.0"
        val constraintLayout = "androidx.constraintlayout:constraintlayout:1.1.3"
        val timber = "com.jakewharton.timber:timber:4.7.1"
    }

    object TestLibs {
        private val androidxTestVersion = "1.2.0"

        val kotlinTestJunit = "org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion"
        val androidxCore = "androidx.test:core:$androidxTestVersion"
        val androidxRunner = "androidx.test:runner:$androidxTestVersion"
        val androidxJunit = "androidx.test.ext:junit:1.1.1"
        val robolectric = "org.robolectric:robolectric:4.3"
        val junit = "junit:junit:4.12"
        val espressoCore = "androidx.test.espresso:espresso-core:3.2.0"
        val androidxOrchestrator = "androidx.test:orchestrator:$androidxTestVersion"
        val mockitoKotlin = "com.nhaarman.mockitokotlin2:mockito-kotlin:2.2.0"
    }

    object QualityPlugins {
        val jacocoVersion = "0.8.4"
        val spotlessVersion = "3.24.2"
    }
}
