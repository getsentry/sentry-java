package io.sentry.kotlin

import io.sentry.IScopes
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SentryCoroutineExceptionHandlerTest {

    class Fixture {
        val scopes = mock<IScopes>()

        fun getSut(): SentryCoroutineExceptionHandler {
            return SentryCoroutineExceptionHandler(scopes)
        }
    }

    @Test
    fun `captures unhandled exception in launch coroutine`() = runTest {
        val fixture = Fixture()
        val handler = fixture.getSut()
        val exception = RuntimeException("test")

        GlobalScope.launch(handler) {
            throw exception
        }.join()

        verify(fixture.scopes).captureEvent(
            check {
                assertSame(exception, it.throwable)
            }
        )
    }

    @Test
    fun `captures unhandled exception in launch coroutine with child`() = runTest {
        val fixture = Fixture()
        val handler = fixture.getSut()
        val exception = RuntimeException("test")

        GlobalScope.launch(handler) {
            launch {
                throw exception
            }.join()
        }.join()

        verify(fixture.scopes).captureEvent(
            check {
                assertSame(exception, it.throwable)
            }
        )
    }

    @Test
    fun `captures unhandled exception in async coroutine`() = runTest {
        val fixture = Fixture()
        val handler = fixture.getSut()
        val exception = RuntimeException("test")

        val deferred = GlobalScope.async() {
            throw exception
        }
        GlobalScope.launch(handler) {
            deferred.await()
        }.join()

        verify(fixture.scopes).captureEvent(
            check {
                assertTrue { exception.toString().equals(it.throwable.toString()) } // stack trace will differ
            }
        )
    }
}
