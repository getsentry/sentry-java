import com.diffplug.spotless.LineEnding

plugins {
    id("com.diffplug.spotless")
}

spotless {
    lineEndings = LineEnding.UNIX
    java {
        target("**/*.java")
        removeUnusedImports()
        googleJavaFormat()
        targetExclude("src/**/java/io/sentry/vendor/**")
    }
    kotlin {
        target("**/*.kt")
        ktlint()
        targetExclude("src/test/java/io/sentry/apollo4/generated/**", "src/test/java/io/sentry/apollo3/adapter/**")
        suppressLintsFor {
            step = "ktlint"
            shortCode = "standard:max-line-length"
        }
        suppressLintsFor {
            step = "ktlint"
            shortCode = "standard:property-naming"
        }
        suppressLintsFor {
            step = "ktlint"
            shortCode = "standard:function-naming"
        }
    }
    kotlinGradle {
        target("**/*.kts")
        ktlint()
    }
}

