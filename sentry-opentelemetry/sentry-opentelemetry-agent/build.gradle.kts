import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

fun relocatePackages(shadowJar: ShadowJar) {
    // rewrite dependencies calling Logger.getLogger
    shadowJar.relocate("java.util.logging.Logger", "io.opentelemetry.javaagent.bootstrap.PatchLogger")

    // rewrite library instrumentation dependencies
    shadowJar.relocate("io.opentelemetry.instrumentation", "io.opentelemetry.javaagent.shaded.instrumentation")

    // relocate OpenTelemetry API usage
    shadowJar.relocate("io.opentelemetry.api", "io.opentelemetry.javaagent.shaded.io.opentelemetry.api")
    shadowJar.relocate("io.opentelemetry.semconv", "io.opentelemetry.javaagent.shaded.io.opentelemetry.semconv")
    shadowJar.relocate("io.opentelemetry.context", "io.opentelemetry.javaagent.shaded.io.opentelemetry.context")

    // relocate the OpenTelemetry extensions that are used by instrumentation modules
    // these extensions live in the AgentClassLoader, and are injected into the user's class loader
    // by the instrumentation modules that use them
    shadowJar.relocate("io.opentelemetry.extension.aws", "io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.aws")
    shadowJar.relocate("io.opentelemetry.extension.kotlin", "io.opentelemetry.javaagent.shaded.io.opentelemetry.extension.kotlin")
}

// this configuration collects libs that will be placed in the bootstrap classloader
val bootstrapLibs = configurations.create("bootstrapLibs") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

// this configuration collects libs that will be placed in the agent classloader, isolated from the instrumented application code
val javaagentLibs = configurations.create("javaagentLibs") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

// this configuration stores the upstream agent dep that's extended by this project
val upstreamAgent = configurations.create("upstreamAgent") {
    isCanBeResolved = true
    isCanBeConsumed = false
}

dependencies {
    bootstrapLibs(projects.sentry)
    javaagentLibs(projects.sentryOpentelemetry.sentryOpentelemetryAgentcustomization)
    upstreamAgent("io.opentelemetry.javaagent:opentelemetry-javaagent:${Config.Libs.otelJavaagentVersion}")
}

fun isolateClasses(jars: Iterable<File>): CopySpec {
    return copySpec {
        jars.forEach {
            from(zipTree(it)) {
                into("inst")
                rename("^(.*)\\.class\$", "\$1.classdata")
                // Rename LICENSE file since it clashes with license dir on non-case sensitive FSs (i.e. Mac)
                rename("^LICENSE\$", "LICENSE.renamed")
                exclude("META-INF/INDEX.LIST")
                exclude("META-INF/*.DSA")
                exclude("META-INF/*.SF")
            }
        }
    }
}

// Don't publish non-shadowed jar (shadowJar is in shadowRuntimeElements)
with(components["java"] as AdhocComponentWithVariants) {
    configurations.forEach {
        withVariantsFromConfiguration(configurations["apiElements"]) {
            skip()
        }
        withVariantsFromConfiguration(configurations["runtimeElements"]) {
            skip()
        }
    }
}

tasks {
    jar {
        archiveClassifier.set("dontuse")
    }

    // building the final javaagent jar is done in 3 steps:

    // 1. all distro specific javaagent libs are relocated
    create("relocateJavaagentLibs", ShadowJar::class.java) {
        configurations = listOf(javaagentLibs)

        duplicatesStrategy = DuplicatesStrategy.FAIL

        archiveFileName.set("javaagentLibs-relocated.jar")

        mergeServiceFiles()
        exclude("**/module-info.class")
        relocatePackages(this)

        // exclude known bootstrap dependencies - they can't appear in the inst/ directory
        dependencies {
            exclude("org.slf4j:slf4j-api")
            exclude("io.opentelemetry:opentelemetry-api")
            exclude("io.opentelemetry:opentelemetry-api-logs")
            exclude("io.opentelemetry:opentelemetry-context")
            exclude("io.opentelemetry:opentelemetry-semconv")
        }
    }

    // 2. the distro javaagent libs are then isolated - moved to the inst/ directory
    // having a separate task for isolating javaagent libs is required to avoid duplicates with the upstream javaagent
    // duplicatesStrategy in shadowJar won't be applied when adding files with with(CopySpec) because each CopySpec has
    // its own duplicatesStrategy
    create("isolateJavaagentLibs", Copy::class.java) {
        dependsOn(findByName("relocateJavaagentLibs"))
        with(isolateClasses(findByName("relocateJavaagentLibs")!!.outputs.files))

        into("$buildDir/isolated/javaagentLibs")
    }

    // 3. the relocated and isolated javaagent libs are merged together with the bootstrap libs (which undergo relocation
    // in this task) and the upstream javaagent jar; duplicates are removed
    shadowJar {
        configurations = listOf(bootstrapLibs, upstreamAgent)

        dependsOn(findByName("isolateJavaagentLibs"))
        from(findByName("isolateJavaagentLibs")!!.outputs)

        archiveClassifier.set("")

        duplicatesStrategy = DuplicatesStrategy.FAIL

        mergeServiceFiles {
            include("inst/META-INF/services/*")
        }
        exclude("**/module-info.class")
        relocatePackages(this)

        manifest {
            attributes.put("Main-Class", "io.opentelemetry.javaagent.OpenTelemetryAgent")
            attributes.put("Agent-Class", "io.opentelemetry.javaagent.OpenTelemetryAgent")
            attributes.put("Premain-Class", "io.opentelemetry.javaagent.OpenTelemetryAgent")
            attributes.put("Can-Redefine-Classes", "true")
            attributes.put("Can-Retransform-Classes", "true")
            attributes.put("Implementation-Vendor", "Sentry")
            attributes.put("Implementation-Version", "sentry-${project.version}-otel-${Config.Libs.otelJavaagentVersion}")
        }
    }

    assemble {
        dependsOn(shadowJar)
    }
}
