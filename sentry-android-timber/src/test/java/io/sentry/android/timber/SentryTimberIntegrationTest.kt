package io.sentry.android.timber

import io.sentry.IHub
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.protocol.SdkVersion
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import timber.log.Timber
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SentryTimberIntegrationTest {

    private class Fixture {
        val hub = mock<IHub>()
        val options = SentryOptions().apply {
            sdkVersion = SdkVersion("test", "1.2.3")
        }

        fun getSut(
            minEventLevel: SentryLevel = SentryLevel.ERROR,
            minBreadcrumbLevel: SentryLevel = SentryLevel.INFO
        ): SentryTimberIntegration {
            return SentryTimberIntegration(
                minEventLevel = minEventLevel,
                minBreadcrumbLevel = minBreadcrumbLevel
            )
        }
    }
    private val fixture = Fixture()

    @BeforeTest
    fun beforeTest() {
        Timber.uprootAll()
    }

    @Test
    fun `Integrations plants a tree into Timber on register`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        assertEquals(1, Timber.treeCount())

        val trees = Timber.forest()
        val first = trees.first()
        assertTrue(first is SentryTimberTree)
    }

    @Test
    fun `Integrations plants the SentryTimberTree tree`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        Timber.e(Throwable())
        verify(fixture.hub).captureEvent(any())
    }

    @Test
    fun `Integrations removes a tree from Timber on close integration`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        assertEquals(1, Timber.treeCount())

        sut.close()
        assertEquals(0, Timber.treeCount())
    }

    @Test
    fun `Integrations do not throw if close is called before register`() {
        val sut = fixture.getSut()
        sut.close()

        assertEquals(0, Timber.treeCount())
    }

    @Test
    fun `Integrations pass the right min levels`() {
        val sut = fixture.getSut(
            minEventLevel = SentryLevel.INFO,
            minBreadcrumbLevel = SentryLevel.DEBUG
        )
        sut.register(fixture.hub, fixture.options)

        assertEquals(sut.minEventLevel, SentryLevel.INFO)
        assertEquals(sut.minBreadcrumbLevel, SentryLevel.DEBUG)
    }

    @Test
    fun `Integration adds itself to the package list`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        assertTrue(
            fixture.options.sdkVersion!!.packageSet.any {
                it.name == "maven:io.sentry:sentry-android-timber" &&
                    it.version == BuildConfig.VERSION_NAME
            }
        )
    }

    @Test
    fun `Integration adds itself to the integration list`() {
        val sut = fixture.getSut()
        sut.register(fixture.hub, fixture.options)

        assertTrue(
            fixture.options.sdkVersion!!.integrationSet.contains("Timber")
        )
    }
}
