plugins {
    `java-platform` // used to build Maven BOM
    `maven-publish`
}

dependencies {
    project.parent!!.subprojects
        .filter { !it.name.startsWith("sentry-samples") && it.name != project.name && it.name != "sentry-test-support" }
        .forEach {
        constraints.add("api", "${it.group}:${it.name}:${it.version}")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
        }
    }
}
