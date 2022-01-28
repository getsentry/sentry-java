package io.sentry.kotlin

import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.sentry.IHub
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertSame

public class SentryCoroutineExceptionHandlerTest {

    private val hub = mock<IHub>()

    @Test
    public fun `Exception handler captures event`() {
        val ex = AssertionError()
        GlobalScope.launch(SentryCoroutineExceptionHandler(hub)) {
            throw ex
        }

        runBlocking {
            verify(hub).captureEvent(
                check {
                    assertSame(ex, it.throwable)
                }
            )
        }
    }
}
