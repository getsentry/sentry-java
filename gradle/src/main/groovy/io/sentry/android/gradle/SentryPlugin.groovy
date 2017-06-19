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

                }
                def cliExecutable = sentryProps.getProperty("cli.executable", "sentry-cli")

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

                        def debugMetaProps = new Properties()
                        debugMetaProps.setProperty("io.sentry.ProguardUuids", "abcd|xyz1")

                        doLast {
                            // TODO: this can be done in sentry-cli, change this to pass args to it?
                            // TODO: this probably isn't a safe/stable way to get the intermediate dir?
                            def assetsDir = "app/build/intermediates/assets/${variant.dirName}"
                            def out = new FileOutputStream("$assetsDir/sentry-debug-meta.properties")
                            debugMetaProps.store(out, null)
                            out.close()
                        }

                        def args = [
                                cliExecutable,
                                "upload-proguard",
                                "--android-manifest",
                                manifestPath,
                                "--update-manifest",
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
                        if (!mappingFile.exists()) {
                            throw new StopExecutionException() as Throwable
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
