package io.sentry.android.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.apache.tools.ant.taskdefs.condition.Os

class SentryPlugin implements Plugin<Project> {
    static final String GROUP_NAME = 'Sentry'

    void apply(Project project) {
        project.extensions.create("sentry", SentryPluginExtension)

        project.afterEvaluate {
            if(!project.plugins.hasPlugin(AppPlugin)) {
                throw new IllegalStateException('Must apply \'com.android.application\' first!')
            }

            project.android.applicationVariants.all { ApplicationVariant variant ->
                def variantOutput = variant.outputs.first()
                def variantName = variant.name.capitalize()
                def manifestPath = variantOutput.processManifest.manifestOutputFile
                def mappingFile = variant.getMappingFile()

                def proguardTask = project.tasks.findByName("transformClassesAndResourcesWithProguardFor${variantName}")
                if (proguardTask == null) {
                    proguardTask = project.tasks.findByName("proguard${variantName}")
                }
                def dexTask = project.tasks.findByName("transformClassesWithDexFor${variantName}")
                if (dexTask == null) {
                    dexTask = project.tasks.findByName("dex${variantName}")
                }

                def rootPath = project.rootDir.toPath().toString()
                def propertiesFile = "${rootPath}/sentry.properties"
                Properties sentryProps = new Properties()
                try {
                    sentryProps.load(new FileInputStream(propertiesFile))
                } catch (FileNotFoundException e) {
                    // it's okay, we can ignore it.
                }

                def cliExecutable = sentryProps.getProperty("cli.executable", "sentry-cli")
                def debugMetaPropPath = "${rootPath}/app/build/intermediates/assets/${variant.dirName}/sentry-debug-meta.properties"

                if (proguardTask != null) {
                    SentryProguardConfigTask proguardConfigTask = project.tasks.create("addSentryProguardSettingsFor${variantName}", SentryProguardConfigTask)
                    proguardConfigTask.group = GROUP_NAME
                    proguardConfigTask.applicationVariant = variant

                    def persistIdsTask = project.tasks.create(
                            name: "persistSentryProguardUuidsFor${variantName}",
                            type: Exec) {
                        description "Write references to proguard UUIDs to the android assets."
                        workingDir rootPath
                        environment("SENTRY_PROPERTIES", propertiesFile)

                        def args = [
                                cliExecutable,
                                "upload-proguard",
                                "--android-manifest",
                                manifestPath,
                                "--write-properties",
                                debugMetaPropPath,
                                mappingFile
                        ]

                        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                            commandLine("cmd", "/c", *args)
                        } else {
                            commandLine(*args)
                        }

                        enabled true
                    }

                    // and run before dex transformation.  If we managed to find the dex task
                    // we set ourselves as dependency, otherwise we just hack outselves into
                    // the proguard task's doLast.
                    if (dexTask != null) {
                        dexTask.dependsOn persistIdsTask
                    } else {
                        proguardTask.doLast {
                            persistIdsTask.execute()
                        }
                    }
                    persistIdsTask.dependsOn proguardTask

                    if (project.sentry.autoProguardConfig) {
                        proguardTask.dependsOn proguardConfigTask
                    }
                }
            }
        }
    }
}
