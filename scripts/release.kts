/**
 * Outputs the bash script that uploads packages to Bintray.
 *
 * This script assumes that all distrbution packages have been downloaded and unzipped in one directory.
 * For example, all packages are in the "dist" directory:
 *
 * dist
 *  ├── sentry-3.1.2-SNAPSHOT
 *  ├── sentry-android-3.1.2-SNAPSHOT
 *  ├── sentry-android-core-3.1.2-SNAPSHOT
 *  ├── sentry-android-ndk-3.1.2-SNAPSHOT
 *  ├── sentry-android-timber-3.1.2-SNAPSHOT
 *  ├── sentry-log4j2-3.1.2-SNAPSHOT
 *  ├── sentry-logback-3.1.2-SNAPSHOT
 *  ├── sentry-servlet-3.1.2-SNAPSHOT
 *  ├── sentry-spring-3.1.2-SNAPSHOT
 *  └── sentry-spring-boot-starter-3.1.2-SNAPSHOT
 *
 * To execute the script two environment variables that are used by Maven have to be present: BINTRAY_USERNAME, BINTRAY_API_KEY
 *
 * Example usage (assuming that the script is executed from the `<project-root>/scripts` directory and the distribution files are in `<project-root>/dist`):
 * $ kotlinc -script release.kts -- -d ../dist  -javaRepositoryUrl https://api.bintray.com/maven/sentry/sentry-java/sentry-java/ -androidRepositoryUrl https://api.bintray.com/maven/sentry/sentry-android/sentry-java/ | sh
 *
 */
import java.io.File

/**
 * Path to a directory with unzipped distribution packages.
 */
val path = argOrDefault("d", ".")

/**
 * Path to Maven settings.xml containing bintray username and api key.
 */
val settingsPath = argOrDefault("s", "./settings.xml")

/**
 * Bintray repository URL for non-Android projects.
 */
val javaRepositoryUrl = requiredArg("javaRepositoryUrl")

/**
 * Bintray repository URL for Android projects.
 */
val androidRepositoryUrl = requiredArg("androidRepositoryUrl")

/**
 * Maven server id in the settings.xml file.
 */
val repositoryId = argOrDefault("repositoryId", "bintray")

/**
 * If package should be published on bintray or just uploaded but not published.
 */
val publish = if (argOrDefault("publish", "false") == "true") 1 else 0

File(path)
    .listFiles { file -> file.isDirectory() }
    .forEach { folder ->
        val path = folder.path
        val module = folder.name

        val file: String
        val repositoryUrl: String

        val androidFile = folder.listFiles { it -> it.name.contains("release") && it.extension == "aar" }.firstOrNull()
        if (androidFile != null) {
            file = androidFile.path
            repositoryUrl = androidRepositoryUrl
        } else {
            file = "$path/$module.jar"
            repositoryUrl = javaRepositoryUrl
        }
        val javadocFile = "$path/$module-javadoc.jar"
        val sourcesFile = "$path/$module-sources.jar"
        val pomFile = "$path/pom-default.xml"

        val command = "./mvnw deploy:deploy-file -Dfile=$file -Dfiles=$javadocFile,$sourcesFile -Dclassifiers=sources,javadoc -Dtypes=jar,jar -DpomFile=$pomFile -DrepositoryId=$repositoryId -Durl=$repositoryUrl\\;publish\\=$publish --settings $settingsPath"
        println(command)
    }

/**
 * Returns the value for a command line argument passed with -argName flag or throws an exception if not provided.
 */
fun Release.requiredArg(argName: String) =
    if (args.contains("-$argName")) args[1 + args.indexOf("-$argName")] else throw Error("$argName parameter must be provided")

/**
 * Returns the value for a command line argument passed with -argName flag or returns the default value.
 */
fun Release.argOrDefault(argName: String, default: String) =
    if (args.contains("-$argName")) args[1 + args.indexOf("-$argName")] else default
