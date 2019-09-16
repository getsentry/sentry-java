plugins {
    java
    kotlin("jvm")
    jacoco
}

dependencies {
    testImplementation(kotlin("stdlib-jdk8"))
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.3.50")
}

configure<SourceSetContainer> {
    test {
        java.srcDir("src/test/java")
    }
}

jacoco {
    toolVersion = "0.8.4"
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
