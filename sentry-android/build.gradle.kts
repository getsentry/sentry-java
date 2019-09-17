plugins {
    id("com.android.library")
    id("kotlin-android")
    jacoco
}

dependencies {
    api(project(":sentry-core"))
    testImplementation("org.robolectric:robolectric:4.3")
    testImplementation(kotlin("stdlib-jdk8"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.3.50")
    testImplementation("androidx.test:core:1.2.0")
    testImplementation("androidx.test:runner:1.2.0")
    testImplementation("androidx.test.ext:junit:1.1.1")
}

android {
    compileSdkVersion(29)
    buildToolsVersion("29.0.2")
    defaultConfig {
        minSdkVersion(14)
        targetSdkVersion(29)
        javaCompileOptions {
            annotationProcessorOptions {
                includeCompileClasspath = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    testOptions {
        animationsDisabled = true
        unitTests.apply {
            isReturnDefaultValues = true
            isIncludeAndroidResources = true
            all(KotlinClosure1<Any, Test>({
                (this as Test).also { testTask ->
                    testTask.extensions
                        .getByType(JacocoTaskExtension::class.java)
                        .isIncludeNoLocationClasses = true
                }
            }, this))
        }
    }
}
