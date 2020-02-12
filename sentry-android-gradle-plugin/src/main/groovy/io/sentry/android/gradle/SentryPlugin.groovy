package io.sentry.android.gradle

import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariantOutput
import org.apache.commons.compress.utils.IOUtils
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.apache.tools.ant.taskdefs.condition.Os

class SentryPlugin implements Plugin<Project> {
    static final String GROUP_NAME = 'Sentry'
    private static final String SENTRY_ORG_PARAMETER = "sentryOrg"
    private static final String SENTRY_PROJECT_PARAMETER = "sentryProject"

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
        project.logger.info("propertiesFile: ${propertiesFile}")

        Properties sentryProps = new Properties()
        try {
            sentryProps.load(new FileInputStream(propertiesFile))
        } catch (FileNotFoundException ignored) {
            project.logger.error(ignored.getMessage())
            // it's okay, we can ignore it.
        }

        def rv = sentryProps.getProperty("cli.executable")
        if (rv != null) {
            return rv
        } else {
            project.logger.info("cli.executable is null")
        }

        // in case there is a version from npm right around the corner use that one.  This
        // is the case for react-native-sentry for instance
        def possibleExePaths = [
                "${project.rootDir.toPath()}/../node_modules/@sentry/cli/bin/sentry-cli",
                "${project.rootDir.toPath()}/../node_modules/sentry-cli-binary/bin/sentry-cli"
        ]

        possibleExePaths.each {
            if ((new File(it)).exists()) {
                project.logger.info("possibleExePaths: ${it}")
                return it
            }
            if ((new File(it + ".exe")).exists()) {
                project.logger.info("possibleExePaths: ${it}.exe")
                return it + ".exe"
            }
            project.logger.info("possibleExePaths files dont exist")
        }

        // next up try a packaged version of sentry-cli
        def cliSuffix
        def osName = System.getProperty("os.name").toLowerCase()
        project.logger.info("osName: ${osName}")

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
        } else {
            project.logger.info("cliSuffix not assigned")
        }

        if (cliSuffix != null) {
            def resPath = "/bin/sentry-cli-${cliSuffix}"
            def fsPath = SentryPlugin.class.getResource(resPath).getFile()

            // if we are not in a jar, we can use the file directly
            if ((new File(fsPath)).exists()) {
                project.logger.info("fsPath: ${fsPath}")
                return fsPath
            } else {
                project.logger.info("fsPath doesnt exist")
            }

            // otherwise we need to unpack into a file
            def resStream = SentryPlugin.class.getResourceAsStream(resPath)
            File tempFile = File.createTempFile(".sentry-cli", ".exe")
            if (tempFile != null) {
                project.logger.info("tempFile: ${tempFile.path}")
            } else {
                project.logger.info("tempFile is null")
            }

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
        def names = [
                // Android Studio 3.3 includes the R8 shrinker.
                "transformClassesAndResourcesWithR8For${variant.name.capitalize()}",
                "transformClassesAndResourcesWithProguardFor${variant.name.capitalize()}"
        ]

        return names.findResult { project.tasks.findByName(it) } ?: project.tasks.findByName("proguard${names[1]}")
    }

    /**
     * Returns the dex task for the given project and variant.
     *
     * @param project
     * @param variant
     * @return
     */
    static Task getDexTask(Project project, ApplicationVariant variant) {
        def names = [
                "transformClassesWithDexFor${variant.name.capitalize()}",
                "transformClassesWithDexBuilderFor${variant.name.capitalize()}",
                "transformClassesAndDexWithShrinkResFor${variant.name.capitalize()}"
        ]

        return names.findResult { project.tasks.findByName(it) } ?: project.tasks.findByName("dex${names[0]}")
    }

    /**
     * Returns the bundle task for the given project and variant.
     *
     * @param project
     * @param variant
     * @return
     */
    static Task getBundleTask(Project project, ApplicationVariant variant) {
        return project.tasks.findByName("build${variant.name.capitalize()}PreBundle")
    }

    /**
     * Returns the path to the debug meta properties file for the given variant.
     *
     * @param project
     * @param variant
     * @return
     */
    static String getDebugMetaPropPath(Project project, ApplicationVariant variant) {
        try {
            return variant.mergeAssetsProvider.get().outputDir.get().file("sentry-debug-meta.properties").getAsFile().path
        } catch (Exception ignored) {
            project.logger.error("getDebugMetaPropPath 1: ${ignored.getMessage()}")
        }

        try {
            return variant.mergeAssets.outputDir.get().file("sentry-debug-meta.properties").getAsFile().path
        } catch (Exception ignored) {
            project.logger.error("getDebugMetaPropPath 2: ${ignored.getMessage()}")
        }

        try {
            return "${variant.mergeAssets.outputDir.get().asFile.path}/sentry-debug-meta.properties"
        } catch (Exception ignored) {
            project.logger.error("getDebugMetaPropPath 3: ${ignored.getMessage()}")
        }

        try {
            return "${variant.mergeAssets.outputDir}/sentry-debug-meta.properties"
        } catch (Exception ignored) {
            project.logger.error("getDebugMetaPropPath 4: ${ignored.getMessage()}")
        }
    }

    void apply(Project project) {
        SentryPluginExtension extension = project.extensions.create("sentry", SentryPluginExtension)

        project.afterEvaluate {
            if (!project.plugins.hasPlugin(AppPlugin) && !project.getPlugins().hasPlugin(LibraryPlugin)) {
                throw new IllegalStateException('Must apply \'com.android.application\' first!')
            }

            project.android.applicationVariants.all { ApplicationVariant variant ->
                variant.outputs.each { variantOutput ->
                    def manifestPath = extension.manifestPath
                    if (manifestPath == null) {
                        def dir = findAndroidManifestFileDir(project, variantOutput)
                        if (dir != null) {
                            project.logger.info("manifestDir: ${dir.path}")
                        } else {
                            project.logger.info("manifestDir is null")
                        }

                        manifestPath = new File(new File(dir, variantOutput.dirName), "AndroidManifest.xml")
                        project.logger.info("manifestPath: ${manifestPath}")
                    } else {
                        project.logger.info("manifestPath: ${manifestPath}")
                    }

                    def mappingFile = variant.getMappingFile()
                    def proguardTask = getProguardTask(project, variant)

                    def dexTask = getDexTask(project, variant)
                    if (dexTask != null) {
                        project.logger.info("dexTask ${dexTask.path}")
                    } else {
                        project.logger.info("dexTask is null")
                    }

                    def bundleTask = getBundleTask(project, variant)
                    if (bundleTask != null) {
                        project.logger.info("bundleTask ${bundleTask.path}")
                    } else {
                        project.logger.info("bundleTask is null")
                    }

                    if (proguardTask == null) {
                        project.logger.info("proguardTask is null")
                        return
                    } else {
                        project.logger.info("proguardTask ${proguardTask.path}")
                    }

//                     create a task to configure proguard automatically unless the user disabled it.
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

                        def propsFile = getPropsString(project, variant)

                        if (propsFile != null) {
                            environment("SENTRY_PROPERTIES", propsFile)
                        } else {
                            project.logger.info("propsFile is null")
                        }

                        def debugMetaPropPath = getDebugMetaPropPath(project, variant)
                        project.logger.info("debugMetaPropPath: ${debugMetaPropPath}")

                        def args = [
                                cli,
                                "upload-proguard",
                                "--android-manifest",
                                manifestPath,
                                "--write-properties",
                                debugMetaPropPath,
                                mappingFile
                        ]

                        if (!extension.autoUpload) {
                            args << "--no-upload"
                        }

                        def buildTypeProperties = variant.buildType.ext
                        if (buildTypeProperties.has(SENTRY_ORG_PARAMETER)) {
                            args.add("--org")
                            args.add(buildTypeProperties.get(SENTRY_ORG_PARAMETER).toString())
                        }
                        if (buildTypeProperties.has(SENTRY_PROJECT_PARAMETER)) {
                            args.add("--project")
                            args.add(buildTypeProperties.get(SENTRY_PROJECT_PARAMETER).toString())
                        }

                        project.logger.info("cli args: ${args.toString()}")

                        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                            commandLine("cmd", "/c", *args)
                        } else {
                            commandLine(*args)
                        }

                        project.logger.info("args executed.")

                        enabled true
                    }

                    // create and hooks the uploading of native symbols task after the assembling task
                    def variantOutputName = "${variant.name.capitalize()}${variantOutput.name.capitalize()}"
                    def uploadNativeSymbolsTaskName = "uploadNativeSymbolsFor${variantOutputName}"
                    def uploadNativeSymbolsTask = project.tasks.create(
                            name: uploadNativeSymbolsTaskName,
                            type: Exec) {
                        description "Uploads Native symbols."
                        workingDir project.rootDir

                        def propsFile = getPropsString(project, variant)

                        if (propsFile != null) {
                            environment("SENTRY_PROPERTIES", propsFile)
                        } else {
                            project.logger.info("propsFile is null")
                        }

                        def nativeArgs = [
                                cli,
                                "upload-dif",
                        ]

                        def buildTypeProperties = variant.buildType.ext
                        if (buildTypeProperties.has(SENTRY_ORG_PARAMETER)) {
                            nativeArgs.add("-o")
                            nativeArgs.add(buildTypeProperties.get(SENTRY_ORG_PARAMETER).toString())
                        }
                        if (buildTypeProperties.has(SENTRY_PROJECT_PARAMETER)) {
                            nativeArgs.add("-p")
                            nativeArgs.add(buildTypeProperties.get(SENTRY_PROJECT_PARAMETER).toString())
                        }

                        // eg absoluteProjectFolderPath/build/intermediates/merged_native_libs/{variant} where {variant} could be debug/release...
                        def symbolsPath = "${project.projectDir}${File.separator}build${File.separator}intermediates${File.separator}merged_native_libs${File.separator}${variant.name}"
                        project.logger.info("symbolsPath: ${symbolsPath}")

                        nativeArgs.add("${symbolsPath}")

                        // only include sources if includeNativeSources is enabled, this is opt-in feature
                        if (extension.includeNativeSources) {
                            nativeArgs.add("--include-sources")
                        }

                        project.logger.info("nativeArgs args: ${nativeArgs.toString()}")

                        if (Os.isFamily(Os.FAMILY_WINDOWS)) {
                            commandLine("cmd", "/c", *nativeArgs)
                        } else {
                            commandLine(*nativeArgs)
                        }

                        project.logger.info("nativeArgs executed.")

                        enabled true
                    }

                    // and run before dex transformation.  If we managed to find the dex task
                    // we set ourselves as dependency, otherwise we just hack outselves into
                    // the proguard task's doLast.
                    if (dexTask != null) {
                        dexTask.dependsOn persistIdsTask
                    } else {
                        proguardTask.finalizedBy persistIdsTask
                    }
                    // To include proguard uuid file into aab, run before bundle task.
                    if (bundleTask != null) {
                        bundleTask.dependsOn persistIdsTask
                    }
                    persistIdsTask.dependsOn proguardTask

                    // find the assemble task
                    def assembleTask = findAssembleTask(variant)
                    if (assembleTask != null) {
                        project.logger.info("assembleTask ${assembleTask.path}")
                    } else {
                        assembleTask.logger.info("assembleTask is null")
                    }

                    // uploadNativeSymbolsTask only will be executed after the assemble task
                    // and also only if uploadNativeSymbols is enabled, this is opt-in feature
                    if (assembleTask != null && extension.uploadNativeSymbols) {
                        assembleTask.finalizedBy uploadNativeSymbolsTask
                    } else {
                        assembleTask.logger.info("uploadNativeSymbolsTask won't be executed")
                    }
                }
            }
        }
    }

    static File findAndroidManifestFileDir(Project project, BaseVariantOutput variantOutput) {
        // Gradle 4.7 introduced the lazy task API and AGP 3.3+ adopts that,
        // so we apparently have a Provider<File> here instead
        // TODO: This will let us depend on the configuration of each flavor's
        // manifest creation task and their transitive dependencies, which in
        // turn prolongs the configuration time accordingly. Evaluate how Gradle's
        // new Task Avoidance API can be used instead.
        // (https://docs.gradle.org/current/userguide/task_configuration_avoidance.html)

        try { // Android Gradle Plugin >= 3.3.0
            return variantOutput.processManifestProvider.get().manifestOutputDirectory.get().asFile
        } catch (Exception ignored) {
            project.logger.error("findAndroidManifestFileDir 1: ${ignored.getMessage()}")
        }

        try { // Android Gradle Plugin >= 3.0.0
            return variantOutput.processManifest.manifestOutputDirectory.get().asFile
        } catch (Exception ignored) {
            project.logger.error("findAndroidManifestFileDir 2: ${ignored.getMessage()}")
        }

        // Android Gradle Plugin < 3.0.0
        try {
            return new File(variantOutput.processManifest.manifestOutputFile).parentFile
        } catch (Exception ignored) {
            project.logger.error("findAndroidManifestFileDir 3: ${ignored.getMessage()}")
        }

        // https://github.com/Tencent/tinker/pull/620/files
        try {
            return variantOutput.processResourcesProvider.get().manifestFile.parentFile
        } catch (Exception ignored) {
            project.logger.error("findAndroidManifestFileDir 4: ${ignored.getMessage()}")
        }

        try {
            return variantOutput.processResources.manifestFile.parentFile
        } catch (Exception ignored) {
            project.logger.error("findAndroidManifestFileDir 5: ${ignored.getMessage()}")
        }
    }

    /**
     * Returns the assemble task
     * @param project the given project
     * @param variant the given variant
     * @return the task if found or null otherwise
     */
    static Task findAssembleTask(ApplicationVariant variant) {
        try {
            return variant.assembleProvider.get()
        } catch (Exception ignored) {
            return variant.assemble
        }
    }

    /**
     * Returns the GString with the current read'ed properties
     * @param project the given project
     * @param variant the given variant
     * @return the GString if found or null otherwise
     */
    static GString getPropsString(Project project, ApplicationVariant variant) {
        def buildTypeName = variant.buildType.name
        def flavorName = variant.flavorName
        // When flavor is used in combination with dimensions, variant.flavorName will be a concatenation
        // of flavors of different dimensions
        def propName = "sentry.properties"
        // current flavor name takes priority
        def possibleProps = []
        variant.productFlavors.each {
            // flavors used with dimension come in second
            possibleProps.push("${project.projectDir}/src/${it.name}/${propName}")
        }

        possibleProps = [
                "${project.projectDir}/src/${buildTypeName}/${propName}",
                "${project.projectDir}/src/${buildTypeName}/${flavorName}/${propName}",
                "${project.projectDir}/src/${flavorName}/${buildTypeName}/${propName}",
                "${project.projectDir}/src/${flavorName}/${propName}",
                "${project.rootDir.toPath()}/src/${flavorName}/${propName}",
        ] + possibleProps + [
                "${project.rootDir.toPath()}/src/${buildTypeName}/${propName}",
                "${project.rootDir.toPath()}/src/${buildTypeName}/${flavorName}/${propName}",
                "${project.rootDir.toPath()}/src/${flavorName}/${buildTypeName}/${propName}",
                // Root sentry.properties is the last to be looked up
                "${project.rootDir.toPath()}/${propName}"
        ]

        def propsFile = null
        possibleProps.each {
            project.logger.info("Looking for Sentry properties at: $it")
            if (propsFile == null && new File(it).isFile()) {
                propsFile = it
                project.logger.info("Found Sentry properties in: $it")
            }
        }

        return propsFile
    }
}
