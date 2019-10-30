plugins {
    java
    kotlin("jvm")
    jacoco
    id("net.ltgt.errorprone")
}

dependencies {
    // Envelopes require JSON. Until a parse is done without GSON, we'll depend on it explicitly here
    implementation(Config.Libs.gson)

    compileOnly(Config.CompileOnly.noopen)
    errorprone(Config.CompileOnly.noopenProne)
    errorprone(Config.CompileOnly.errorprone)
    errorproneJavac(Config.CompileOnly.errorProneJavac)
    compileOnly(Config.CompileOnly.annotations)

    // tests
    testImplementation(kotlin(Config.kotlinStdLib))
    testImplementation(Config.TestLibs.kotlinTestJunit)
    testImplementation(Config.TestLibs.mockitoKotlin)
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

jacoco {
    toolVersion = Config.QualityPlugins.jacocoVersion
}

tasks.jacocoTestReport {
    reports {
        xml.isEnabled = true
        html.isEnabled = false
    }
}

tasks {
    jacocoTestCoverageVerification {
        violationRules {
            // TODO: Raise the minimum to a sensible value.
            rule { limit { minimum = BigDecimal.valueOf(0.1) } }
        }
    }
    check {
        dependsOn(jacocoTestCoverageVerification)
        dependsOn(jacocoTestReport)
    }
}
