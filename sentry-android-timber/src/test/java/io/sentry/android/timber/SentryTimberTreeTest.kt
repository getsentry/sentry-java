package io.sentry.android.timber

import io.sentry.Breadcrumb
import io.sentry.IScopes
import io.sentry.SentryLevel
import io.sentry.getExc
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import timber.log.Timber
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SentryTimberTreeTest {

    private class Fixture {
        val scopes = mock<IScopes>()

        fun getSut(
            minEventLevel: SentryLevel = SentryLevel.ERROR,
            minBreadcrumbLevel: SentryLevel = SentryLevel.INFO
        ): SentryTimberTree {
            return SentryTimberTree(scopes, minEventLevel, minBreadcrumbLevel)
        }
    }

    private val fixture = Fixture()

    @BeforeTest
    fun beforeTest() {
        Timber.uprootAll()
    }

    @Test
    fun `Tree captures an event if min level is equal`() {
        val sut = fixture.getSut()
        sut.e(Throwable())
        verify(fixture.scopes).captureEvent(any())
    }

    @Test
    fun `Tree captures an event if min level is higher`() {
        val sut = fixture.getSut()
        sut.wtf(Throwable())
        verify(fixture.scopes).captureEvent(any())
    }

    @Test
    fun `Tree won't capture an event if min level is lower`() {
        val sut = fixture.getSut()
        sut.d(Throwable())
        verify(fixture.scopes, never()).captureEvent(any())
    }

    @Test
    fun `Tree captures debug level event`() {
        val sut = fixture.getSut(SentryLevel.DEBUG)
        sut.d(Throwable())
        verify(fixture.scopes).captureEvent(
            check {
                assertEquals(SentryLevel.DEBUG, it.level)
            }
        )
    }

    @Test
    fun `Tree captures info level event`() {
        val sut = fixture.getSut(SentryLevel.DEBUG)
        sut.i(Throwable())
        verify(fixture.scopes).captureEvent(
            check {
                assertEquals(SentryLevel.INFO, it.level)
            }
        )
    }

    @Test
    fun `Tree captures warning level event`() {
        val sut = fixture.getSut(SentryLevel.DEBUG)
        sut.w(Throwable())
        verify(fixture.scopes).captureEvent(
            check {
                assertEquals(SentryLevel.WARNING, it.level)
            }
        )
    }

    @Test
    fun `Tree captures error level event`() {
        val sut = fixture.getSut(SentryLevel.DEBUG)
        sut.e(Throwable())
        verify(fixture.scopes).captureEvent(
            check {
                assertEquals(SentryLevel.ERROR, it.level)
            }
        )
    }

    @Test
    fun `Tree captures fatal level event`() {
        val sut = fixture.getSut(SentryLevel.DEBUG)
        sut.wtf(Throwable())
        verify(fixture.scopes).captureEvent(
            check {
                assertEquals(SentryLevel.FATAL, it.level)
            }
        )
    }

    @Test
    fun `Tree captures unknown as debug level event`() {
        val sut = fixture.getSut(SentryLevel.DEBUG)
        sut.log(15, Throwable())
        verify(fixture.scopes).captureEvent(
            check {
                assertEquals(SentryLevel.DEBUG, it.level)
            }
        )
    }

    @Test
    fun `Tree captures an event with an exception`() {
        val sut = fixture.getSut()
        val throwable = Throwable()
        sut.e(throwable)
        verify(fixture.scopes).captureEvent(
            check {
                assertEquals(throwable, it.getExc())
            }
        )
    }

    @Test
    fun `Tree captures an event without an exception`() {
        val sut = fixture.getSut()
        sut.e("message")
        verify(fixture.scopes).captureEvent(
            check {
                assertNull(it.getExc())
            }
        )
    }

    @Test
    fun `Tree captures an event and sets Timber as a logger`() {
        val sut = fixture.getSut()
        sut.e("message")
        verify(fixture.scopes).captureEvent(
            check {
                assertEquals("Timber", it.logger)
            }
        )
    }

    @Test
    fun `Tree captures an event with TimberTag tag`() {
        val sut = fixture.getSut()
        Timber.plant(sut)
        // only available thru static class
        Timber.tag("tag")
        Timber.e("message")
        verify(fixture.scopes).captureEvent(
            check {
                assertEquals("tag", it.getTag("TimberTag"))
            }
        )
    }

    @Test
    fun `Tree captures an event without TimberTag tag`() {
        val sut = fixture.getSut()
        Timber.plant(sut)
        Timber.e("message")
        verify(fixture.scopes).captureEvent(
            check {
                assertNull(it.getTag("TimberTag"))
            }
        )
    }

    @Test
    fun `Tree captures an event with given message`() {
        val sut = fixture.getSut()
        sut.e("message")
        verify(fixture.scopes).captureEvent(
            check {
                assertNotNull(it.message) { message ->
                    assertEquals("message", message.message)
                }
            }
        )
    }

    @Test
    fun `Tree captures an event with formatted message and arguments, when provided`() {
        val sut = fixture.getSut()
        sut.e("test count: %d", 32)
        verify(fixture.scopes).captureEvent(
            check {
                assertNotNull(it.message) { message ->
                    assertEquals("test count: %d", message.message)
                    assertEquals("test count: 32", message.formatted)
                    assertEquals("32", message.params!!.first())
                }
            }
        )
    }

    @Test
    fun `Tree adds a breadcrumb with formatted message and arguments, when provided`() {
        val sut = fixture.getSut()
        sut.e("test count: %d", 32)

        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("test count: 32", it.message)
            }
        )
    }

    @Test
    fun `Tree adds a breadcrumb if min level is equal`() {
        val sut = fixture.getSut()
        sut.i(Throwable("test"))
        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `Tree adds a breadcrumb if min level is higher`() {
        val sut = fixture.getSut()
        sut.e(Throwable("test"))
        verify(fixture.scopes).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `Tree won't add a breadcrumb if min level is lower`() {
        val sut = fixture.getSut(minBreadcrumbLevel = SentryLevel.ERROR)
        sut.i(Throwable("test"))
        verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `Tree adds an info breadcrumb`() {
        val sut = fixture.getSut()
        sut.i("message")
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("Timber", it.category)
                assertEquals(SentryLevel.INFO, it.level)
                assertEquals("message", it.message)
            }
        )
    }

    @Test
    fun `Tree adds an error breadcrumb`() {
        val sut = fixture.getSut()
        sut.e(Throwable("test"))
        verify(fixture.scopes).addBreadcrumb(
            check<Breadcrumb> {
                assertEquals("exception", it.category)
                assertEquals(SentryLevel.ERROR, it.level)
                assertEquals("test", it.message)
            }
        )
    }

    @Test
    fun `Tree does not add a breadcrumb, if no message provided`() {
        val sut = fixture.getSut()
        sut.e(Throwable())
        verify(fixture.scopes, never()).addBreadcrumb(any<Breadcrumb>())
    }

    @Test
    fun `Tree does not throw when using log with args`() {
        val sut = fixture.getSut()
        sut.d("test %s, %s", 1, 1)
    }
}
