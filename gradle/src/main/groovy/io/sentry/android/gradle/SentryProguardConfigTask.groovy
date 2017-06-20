package io.sentry.android.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import com.android.build.gradle.api.ApplicationVariant

class SentryProguardConfigTask extends DefaultTask {

    ApplicationVariant applicationVariant

    SentryProguardConfigTask() {
        super()
        this.description = "Adds the Sentry recommended proguard settings to your project."
    }

    @TaskAction
    def createProguardConfig() {
        def file = project.file("build/intermediates/sentry/sentry.pro")
        file.getParentFile().mkdirs()
        FileWriter f = new FileWriter(file.path)
        f.write("-keepattributes LineNumberTable,SourceFile\n" +
                "-dontwarn com.facebook.fbui.**\n" +
                "-dontwarn org.slf4j.**\n" +
                "-dontwarn javax.**\n")
        f.close()
        applicationVariant.getBuildType().buildType.proguardFiles(file)
    }
}
