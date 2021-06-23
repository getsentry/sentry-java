/**
 * Outputs the bash script that uploads packages to MavenCentral.
 *
 * This script assumes that all distrbution packages have been downloaded and unzipped in one directory.
 * For example, all packages are in the "distributions" directory:
 *
 * distributions
 *  ├── sentry-3.1.2
 *  ├── sentry-android-3.1.2
 *  ├── sentry-android-core-3.1.2
 *  ├── sentry-android-ndk-3.1.2
 *  ├── sentry-android-timber-3.1.2
 *  ├── sentry-log4j2-3.1.2
 *  ├── sentry-logback-3.1.2
 *  ├── sentry-servlet-3.1.2
 *  ├── sentry-spring-3.1.2
 *  └── sentry-spring-boot-starter-3.1.2
 *
 * To execute the script two environment variables that are used by Maven have to be present: OSSRH_USERNAME, OSSRH_PASSWORD
 *
 * Example usage (assuming that the script is executed from the `<project-root>/scripts` directory and the distribution files are in `<project-root>/distributions`):
 * $ kotlinc -script release.kts -- -d ../distributions | sh
 *
 */
import java.io.File

/**
 * Path to a directory with unzipped distribution packages.
 */
val path = argOrDefault("d", ".")

/**
 * Path to Maven settings.xml containing MavenCentral username and api key.
 */
val settingsPath = argOrDefault("s", "./settings.xml")

/**
 * Maven repository URL.
 */
val repositoryUrl = argOrDefault("repositoryUrl", "https://oss.sonatype.org/service/local/staging/deploy/maven2/")

/**
 * Maven server id in the settings.xml file.
 */
val repositoryId = argOrDefault("repositoryId", "ossrh")

File(path)
        .listFiles { file -> file.isDirectory() }
        .forEach { folder ->
            val path = folder.path
            val module = folder.name
            val pomFile = "$path/pom-default.xml"

            val file: String

            val androidFile = folder
                    .listFiles { it -> it.name.contains("release") && it.extension == "aar" }
                    .firstOrNull()

            val bomFile = folder
                .listFiles { it -> it.extension == "jar" }
                .isEmpty()

            if (bomFile) {
                val command = "./mvnw gpg:sign-and-deploy-file " +
                    "-Dfile=$pomFile " +
                    "-DpomFile=$pomFile " +
                    "-DrepositoryId=$repositoryId " +
                    "-Durl=$repositoryUrl " +
                    "--settings $settingsPath"
                println(command)
            } else {
                if (androidFile != null) {
                    file = androidFile.path
                } else {
                    file = "$path/$module.jar"
                }

                val javadocFile = "$path/$module-javadoc.jar"
                val sourcesFile = "$path/$module-sources.jar"

                // requires GnuPG installed to sign files
                // using 'gpg:sign-and-deploy-file' because 'deploy:deploy-file' does not upload
                // .asc files.
                // TODO: find out where to set keyId, password and secretKeyRingFile if you have
                // more than one.
                val command = "./mvnw gpg:sign-and-deploy-file " +
                    "-Dfile=$file " +
                    "-Dfiles=$javadocFile,$sourcesFile " +
                    "-Dclassifiers=javadoc,sources " +
                    "-Dtypes=jar,jar " +
                    "-DpomFile=$pomFile " +
                    "-DrepositoryId=$repositoryId " +
                    "-Durl=$repositoryUrl " +
                    "--settings $settingsPath"
                println(command)
            }
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
