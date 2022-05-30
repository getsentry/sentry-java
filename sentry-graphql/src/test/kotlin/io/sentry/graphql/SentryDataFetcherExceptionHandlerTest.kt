package io.sentry.graphql

import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import io.sentry.Hint
import io.sentry.IHub
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
