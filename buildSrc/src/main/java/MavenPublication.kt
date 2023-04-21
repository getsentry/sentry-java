/*
 * Adapted from https://github.com/androidx/androidx/blob/c799cba927a71f01ea6b421a8f83c181682633fb/buildSrc/private/src/main/kotlin/androidx/build/MavenUploadHelper.kt#L524-L549
 *
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.vanniktech.maven.publish.MavenPublishBaseExtension
import groovy.util.Node

private val androidLibs = setOf(
    "sentry-android-core",
    "sentry-android-ndk",
    "sentry-android-fragment",
    "sentry-android-navigation",
    "sentry-android-okhttp",
    "sentry-android-timber",
    "sentry-compose-android"
)

private val androidXLibs = listOf(
    "androidx.core:core",
    "androidx.lifecycle:lifecycle-process",
    "androidx.lifecycle:lifecycle-common-java8"
)

@Suppress("UnstableApiUsage")
fun MavenPublishBaseExtension.assignAarTypes() {
    pom {
        withXml {
            // workaround for https://github.com/gradle/gradle/issues/3170
            val dependencies = asNode().children().find {
                it is Node && it.name().toString().endsWith("dependencies")
            } as Node?

            dependencies?.children()?.forEach { dep ->
                if (dep !is Node) {
                    return@forEach
                }
                val group = dep.children().firstOrNull {
                    it is Node && it.name().toString().endsWith("groupId")
                } as? Node
                val groupValue = group?.children()?.firstOrNull() as? String

                val artifactId = dep.children().firstOrNull {
                    it is Node && it.name().toString().endsWith("artifactId")
                } as? Node
                val artifactIdValue = artifactId?.children()?.firstOrNull() as? String

                if (artifactIdValue in androidLibs) {
                    dep.appendNode("type", "aar")
                } else if ("$groupValue:$artifactIdValue" in androidXLibs) {
                    dep.appendNode("type", "aar")
                }
            }
        }
    }
}
