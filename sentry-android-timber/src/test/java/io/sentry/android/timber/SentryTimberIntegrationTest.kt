package io.sentry.android.timber

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.core.IHub
import io.sentry.core.SentryLevel
import io.sentry.core.SentryOptions
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import timber.log.Timber

class SentryTimberIntegrationTest {

    private class Fixture {
        val hub = mock<IHub>()
        val options = SentryOptions()

        fun getSut(
            minEventLevel: SentryLevel = SentryLevel.ERROR,
            minBreadcrumbLevel: SentryLevel = SentryLevel.INFO
        ): SentryTimberIntegration {
            return SentryTimberIntegration(minEventLevel = minEventLevel,
                    minBreadcrumbLevel = minBreadcrumbLevel)
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
        val sut = fixture.getSut(minEventLevel = SentryLevel.INFO,
                minBreadcrumbLevel = SentryLevel.DEBUG)
        sut.register(fixture.hub, fixture.options)

        assertEquals(sut.minEventLevel, SentryLevel.INFO)
        assertEquals(sut.minBreadcrumbLevel, SentryLevel.DEBUG)
    }
}
