plugins {
    `java-platform` // used to build Maven BOM
    `maven-publish`
}

dependencies {
    constraints {
        project.rootProject.subprojects
            .filter {
                !it.name.startsWith("sentry-samples") &&
                    it.name != project.name &&
                    it.name != "sentry-test-support"
            }
            .forEach {
                api(it)
            }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
        }
    }
}
