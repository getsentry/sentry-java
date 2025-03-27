package io.sentry

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SentryIntegrationPackageStorageTest {

    @AfterTest
    fun teardown() {
        SentryIntegrationPackageStorage.getInstance().clearStorage()
    }

    @Test
    fun `same package version is OK`() {
        val storage = SentryIntegrationPackageStorage.getInstance()
        storage.clearStorage()

        storage.addPackage("maven:io.sentry:sentry", BuildConfig.VERSION_NAME)
        storage.addPackage("maven:io.sentry:sentry-logback", BuildConfig.VERSION_NAME)

        assertFalse(storage.checkForMixedVersions(SystemOutLogger()))
    }

    @Test
    fun `checking twice works`() {
        val storage = SentryIntegrationPackageStorage.getInstance()
        storage.clearStorage()

        storage.addPackage("maven:io.sentry:sentry", BuildConfig.VERSION_NAME)
        storage.addPackage("maven:io.sentry:sentry-logback", BuildConfig.VERSION_NAME)

        assertFalse(storage.checkForMixedVersions(SystemOutLogger()))
        assertFalse(storage.checkForMixedVersions(SystemOutLogger()))
    }

    @Test
    fun `checking twice with changes works`() {
        val storage = SentryIntegrationPackageStorage.getInstance()
        storage.clearStorage()

        storage.addPackage("maven:io.sentry:sentry", BuildConfig.VERSION_NAME)
        storage.addPackage("maven:io.sentry:sentry-logback", BuildConfig.VERSION_NAME)

        assertFalse(storage.checkForMixedVersions(SystemOutLogger()))

        storage.addPackage("maven:io.sentry:sentry-spring", "8.0.0")
        assertTrue(storage.checkForMixedVersions(SystemOutLogger()))
    }

    @Test
    fun `mixed package version is not OK`() {
        val storage = SentryIntegrationPackageStorage.getInstance()
        storage.clearStorage()

        storage.addPackage("maven:io.sentry:sentry", BuildConfig.VERSION_NAME)
        storage.addPackage("maven:io.sentry:sentry-logback", "8.0.0")

        assertTrue(storage.checkForMixedVersions(SystemOutLogger()))
    }

    @Test
    fun `only java sdk modules are checked`() {
        val storage = SentryIntegrationPackageStorage.getInstance()
        storage.clearStorage()

        storage.addPackage("maven:io.sentry:sentry", BuildConfig.VERSION_NAME)
        storage.addPackage("maven:io.sentry:sentry-logback", BuildConfig.VERSION_NAME)
        storage.addPackage("maven:io.sentry.other:sentry-other", "1.0.0")
        storage.addPackage("maven:io.opentelemetry.javaagent:opentelemetry-javaagent", "2.0.0")

        assertFalse(storage.checkForMixedVersions(SystemOutLogger()))
    }
}
