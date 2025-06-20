import com.diffplug.spotless.LineEnding

plugins {
    id("com.diffplug.spotless")
}

spotless {
    lineEndings = LineEnding.UNIX
    java {
        target("src/*/java/**/*.java")
        removeUnusedImports()
        googleJavaFormat()
        targetExclude("src/**/java/io/sentry/vendor/**")
    }
    kotlin {
        target("src/*/kotlin/**/*.kt", "src/*/java/**/*.kt")
        ktfmt().googleStyle()
        targetExclude("src/test/java/io/sentry/apollo4/generated/**", "src/test/java/io/sentry/apollo3/adapter/**")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt().googleStyle()
    }
}

