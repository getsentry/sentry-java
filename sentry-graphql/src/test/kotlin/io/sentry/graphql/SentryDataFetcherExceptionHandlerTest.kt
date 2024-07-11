package io.sentry.graphql

import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import io.sentry.Hint
import io.sentry.IScopes
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test

class SentryDataFetcherExceptionHandlerTest {

    @Test
    fun `passes exception to Sentry and invokes delegate`() {
        val scopes = mock<IScopes>()
        val delegate = mock<DataFetcherExceptionHandler>()
        val handler = SentryDataFetcherExceptionHandler(scopes, delegate)

        val exception = RuntimeException()
        val parameters = DataFetcherExceptionHandlerParameters.newExceptionParameters().exception(exception).build()
        handler.onException(parameters)

        verify(scopes).captureException(eq(exception), anyOrNull<Hint>())
        verify(delegate).handleException(parameters)
    }
}
