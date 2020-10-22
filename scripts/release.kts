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
 * $ kotlinc -script release.kts -- -d ../dist -s  -repositoryUrl https://api.bintray.com/maven/sentry/sentry-java/sentry-java/ | sh
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
 * Bintray repository URL.
 */
val repositoryUrl = requiredArg("repositoryUrl")

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

        val androidFile = folder.listFiles { file -> file.name.contains("release") && file.extension == "aar" }.firstOrNull()
        val file = if (androidFile != null) {
            androidFile.path
        } else {
            "$path/$module.jar"
        }
        val javadocFile = "$path/$module-javadoc.jar"
        val sourcesFile = "$path/$module-sources.jar"
        val pomFile = "$path/pom-default.xml"

        val command = "mvn deploy:deploy-file -Dfile=$file -Dfiles=$javadocFile,$sourcesFile -Dclassifiers=sources,javadoc -Dtypes=jar,jar -DpomFile=$pomFile -DrepositoryId=$repositoryId -Durl=$repositoryUrl\\;publish\\=$publish --settings $settingsPath"
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
