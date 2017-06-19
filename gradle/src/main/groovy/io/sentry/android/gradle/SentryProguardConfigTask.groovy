package io.sentry.android.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

import com.android.build.gradle.api.ApplicationVariant

class SentryProguardConfigTask extends DefaultTask {
    static final String PROGUARD_CONFIG_PATH = "build/intermediates/sentry/sentry.pro"
    static final String PROGUARD_CONFIG_SETTINGS = """\
    -keepattributes LineNumberTable,SourceFile
    """

    ApplicationVariant applicationVariant

    SentryProguardConfigTask() {
        super()
        this.description = "Adds the Sentry recommended proguard settings to your project"
    }

    @TaskAction
    def createProguardConfig() {
        def file = project.file(PROGUARD_CONFIG_PATH)
        file.getParentFile().mkdirs()
        FileWriter f = new FileWriter(file.path)
        f.write(PROGUARD_CONFIG_SETTINGS)
        f.close()
        applicationVariant.getBuildType().buildType.proguardFiles(file)
    }
}
