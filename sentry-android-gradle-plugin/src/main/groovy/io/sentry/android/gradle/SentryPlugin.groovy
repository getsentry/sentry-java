package io.sentry.android.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApplicationVariant
import org.apache.commons.compress.utils.IOUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.apache.tools.ant.taskdefs.condition.Os

class SentryPlugin implements Plugin<Project> {
    static final String GROUP_NAME = 'Sentry'

    /**
     * Return the correct sentry-cli executable path to use for the given project.  This
     * will look for a sentry-cli executable in a local node_modules in case it was put
     * there by sentry-react-native or others before falling back to the global installation.
     *
     * @param project
     * @return
     */
    static String getSentryCli(Project project) {
        // if a path is provided explicitly use that first
        def propertiesFile = "${project.rootDir.toPath()}/sentry.properties"
        Properties sentryProps = new Properties()
        try {
            sentryProps.load(new FileInputStream(propertiesFile))
        } catch (FileNotFoundException e) {
            // it's okay, we can ignore it.
        }

        def rv = sentryProps.getProperty("cli.executable")
        if (rv != null) {
            return rv
        }

        // in case there is a version from npm right around the corner use that one.  This
        // is the case for react-native-sentry for instance
        def possibleExePaths = [
            "${project.rootDir.toPath()}/../node_modules/@sentry/cli/bin/sentry-cli",
            "${project.rootDir.toPath()}/../node_modules/sentry-cli-binary/bin/sentry-cli"
        ]

        possibleExePaths.each {
            if ((new File(it)).exists()) {
                return it
            }
            if ((new File(it + ".exe")).exists()) {
                return it + ".exe"
            }
        }

        // next up try a packaged version of sentry-cli
        def cliSuffix
        def osName = System.getProperty("os.name").toLowerCase()
        if (osName.indexOf("mac") >= 0) {
            cliSuffix = "Darwin-x86_64"
        } else if (osName.indexOf("linux") >= 0) {
            def arch = System.getProperty("os.arch")
            if (arch == "amd64") {
                arch = "x86_64"
            }
            cliSuffix = "Linux-" + arch
        } else if (osName.indexOf("win") >= 0) {
            cliSuffix = "Windows-i686.exe"
        }

        if (cliSuffix != null) {
            def resPath = "/bin/sentry-cli-${cliSuffix}"
            def fsPath = SentryPlugin.class.getResource(resPath).getFile()

            // if we are not in a jar, we can use the file directly
            if ((new File(fsPath)).exists()) {
                return fsPath
            }

            // otherwise we need to unpack into a file
            def resStream = SentryPlugin.class.getResourceAsStream(resPath)
            File tempFile = File.createTempFile(".sentry-cli", ".exe")
            tempFile.deleteOnExit()
            def out = new FileOutputStream(tempFile)
            try {
                IOUtils.copy(resStream, out)
            } finally {
                out.close()
            }
            tempFile.setExecutable(true)
            return tempFile.getAbsolutePath()
        }

        return "sentry-cli"
    }

    /**
     * Returns the proguard task for the given project and variant.
     *
     * @param project
     * @param variant
     * @return
     */
    static Task getProguardTask(Project project, ApplicationVariant variant) {
        def name = "transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}"
        def rv = project.tasks.findByName(name)
        if (rv != null) {
            return rv
        }
        return project.tasks.findByName("proguard${name}")
    }

    /**
     * Returns the dex task for the given project and variant.
     *
     * @param project
     * @param variant
     * @return
     */
    static Task getDexTask(Project project, ApplicationVariant variant) {
        def name = "transformClassesWithDexFor${variant.name.capitalize()}"
        def rv = project.tasks.findByName(name)
        if (rv != null) {
            return rv
        }
        return project.tasks.findByName("dex${name}")
    }

    /**
     * Returns the path to the debug meta properties file for the given variant.
     *
     * @param project
     * @param variant
     * @return
     */
    static String getDebugMetaPropPath(Project project, ApplicationVariant variant) {
        return "${variant.mergeAssets.outputDir}/sentry-debug-meta.properties"
    }

    void apply(Project project) {
        SentryPluginExtension extension = project.extensions.create("sentry", SentryPluginExtension)

        project.afterEvaluate {
            if(!project.plugins.hasPlugin(AppPlugin) && !project.getPlugins().hasPlugin(LibraryPlugin)) {
                throw new IllegalStateException('Must apply \'com.android.application\' first!')
            }

            project.android.applicationVariants.all { ApplicationVariant variant ->
                variant.outputs.each { variantOutput ->
                    def manifestPath = extension.manifestPath
                    if (manifestPath == null) {
                        try {
                            // Android Gradle Plugin < 3.0.0
                            manifestPath = variantOutput.processManifest.manifestOutputFile
                        } catch (Exception ignored) {
                            // Android Gradle Plugin >= 3.0.0
                            def outputDir = variantOutput.processManifest.manifestOutputDirectory
                            // Gradle 4.7 introduced the lazy task API and AGP 3.3+ adopts that,
                            // so we apparently have a Provider<File> here instead
                            // TODO: This will let us depend on the configuration of each flavor's
                            // manifest creation task and their transitive dependencies, which in
                            // turn prolongs the configuration time accordingly. Evaluate how Gradle's
                            // new Task Avoidance API can be used instead.
                            // (https://docs.gradle.org/current/userguide/task_configuration_avoidance.html)
                            if (!(outputDir instanceof File)) {
                                outputDir = outputDir.get().asFile
                            }
                            manifestPath = new File(outputDir, "AndroidManifest.xml")
                        }
                    }

                    def mappingFile = variant.getMappingFile()
                    def proguardTask = getProguardTask(project, variant)
                    def dexTask = getDexTask(project, variant)

                    if (proguardTask == null) {
                        return
                    }

                    // create a task to configure proguard automatically unless the user disabled it.
                    if (extension.autoProguardConfig) {
                        def addProguardSettingsTaskName = "addSentryProguardSettingsFor${variant.name.capitalize()}"
                        if (!project.tasks.findByName(addProguardSettingsTaskName)) {
                            SentryProguardConfigTask proguardConfigTask = project.tasks.create(
                                    addProguardSettingsTaskName,
                                    SentryProguardConfigTask)
                            proguardConfigTask.group = GROUP_NAME
                            proguardConfigTask.applicationVariant = variant
                            proguardTask.dependsOn proguardConfigTask
                        }
                    }

                    def cli = getSentryCli(project)

                    def persistIdsTaskName = "persistSentryProguardUuidsFor${variant.name.capitalize()}${variantOutput.name.capitalize()}"
                    // create a task that persists our proguard uuid as android asset
                    def persistIdsTask = project.tasks.create(
                            name: persistIdsTaskName,
                            type: Exec) {
                        description "Write references to proguard UUIDs to the android assets."
                        workingDir project.rootDir

                        def variantName = variant.buildType.name
                        def flavorName = variant.flavorName
                        def propName = "sentry.properties"
                        def possibleProps = [
                                "${project.projectDir}/src/${variantName}/${propName}",
                                "${project.projectDir}/src/${flavorName}/${propName}",
                                "${project.projectDir}/src/${variantName}/${flavorName}/${propName}",
                                "${project.projectDir}/src/${flavorName}/${variantName}/${propName}",
                                "${project.rootDir.toPath()}/src/${variantName}/${propName}",
                                "${project.rootDir.toPath()}/src/${flavorName}/${propName}",
                                "${project.rootDir.toPath()}/src/${variantName}/${flavorName}/${propName}",
                                "${project.rootDir.toPath()}/src/${flavorName}/${variantName}/${propName}",
                                "${project.rootDir.toPath()}/${propName}"
                        ]

                        def propsFile = null
                        possibleProps.each {
                            if (propsFile == null && new File(it).isFile()) {
                                propsFile = it
                            }
                        }

                        if (propsFile != null) {
                            environment("SENTRY_PROPERTIES", propsFile)
                        }

                        def args = [
                                cli,
                                "upload-proguard",
                                "--android-manifest",
                                manifestPath,
                                "--write-properties",
                                getDebugMetaPropPath(project, variant),
                                mappingFile
                        ]

                        if (!extension.autoUpload) {
                            args.push("--no-upload")
                        }

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
                }
            }
        }
    }
}
