package io.sentry.android.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.api.ApplicationVariant
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.StopExecutionException
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
                    SentryProguardConfigTask proguardConfigTask = project.tasks.create("processSentry${variantName}Proguard", SentryProguardConfigTask)
                    proguardConfigTask.group = GROUP_NAME
                    proguardConfigTask.applicationVariant = variant

                    def manifestTask = project.tasks.create(
                            name: "processSentry${variantName}Manifest",
                            type: Exec) {
                        description "Updates the AndroidManifest to contain references to generated proguard files."
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

                    manifestTask.doFirst {
                        if (mappingFile.exists()) {
                            throw new StopExecutionException() as Throwable
                        } else {
                            logger.warn("No mapping file ${mappingFile}. Did you use -dontobfuscate?")
                        }
                    }

                    // and run before dex transformation
                    proguardTask.doLast {
                        manifestTask.execute()
                    }
                    manifestTask.dependsOn proguardTask

                    if (project.sentry.autoProguardConfig) {
                        variantOutput.packageApplication.dependsOn proguardConfigTask
                    }
                }
            }
        }
    }
}
