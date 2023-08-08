package io.sentry.graphql

import graphql.GraphQLContext
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.schema.DataFetchingEnvironmentImpl
import io.sentry.IHub
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SentryGenericDataFetcherExceptionHandlerTest {

    @Test
    fun `collects exception into GraphQLContext and invokes delegate`() {
        val hub = mock<IHub>()
        val delegate = mock<DataFetcherExceptionHandler>()
        val handler = SentryGenericDataFetcherExceptionHandler(
            hub,
            delegate
        )

        val exception = RuntimeException()
        val parameters = DataFetcherExceptionHandlerParameters.newExceptionParameters().exception(exception).dataFetchingEnvironment(
            DataFetchingEnvironmentImpl.newDataFetchingEnvironment().graphQLContext(
                GraphQLContext.of(
                    emptyMap<String, Object>()
                )
            ).build()
        ).build()
        handler.onException(parameters)

        val exceptions: List<Throwable> = parameters.dataFetchingEnvironment.graphQlContext[SentryInstrumentation.SENTRY_EXCEPTIONS_CONTEXT_KEY]
        assertNotNull(exceptions)
        assertEquals(1, exceptions.size)
        assertEquals(exception, exceptions.first())
        verify(delegate).onException(parameters)
    }
}
