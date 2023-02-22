package io.sentry.graphql

import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import io.sentry.Hint
import io.sentry.IHub
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test

class SentryDataFetcherExceptionHandlerTest {

    @Test
    fun `passes exception to Sentry and invokes delegate`() {
        val hub = mock<IHub>()
        val delegate = mock<DataFetcherExceptionHandler>()
        val handler = SentryDataFetcherExceptionHandler(hub, delegate)

        val exception = RuntimeException()
        val parameters = DataFetcherExceptionHandlerParameters.newExceptionParameters().exception(exception).build()
        handler.onException(parameters)

        verify(hub).captureException(eq(exception), anyOrNull<Hint>())
        verify(delegate).onException(parameters)
    }
}
