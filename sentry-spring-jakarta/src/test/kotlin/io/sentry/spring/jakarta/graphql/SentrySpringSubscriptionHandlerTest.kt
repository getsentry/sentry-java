package io.sentry.spring.jakarta.graphql

import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.language.Document
import graphql.language.OperationDefinition
import graphql.schema.DataFetchingEnvironment
import io.sentry.IScopes
import io.sentry.graphql.ExceptionReporter
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.same
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.graphql.execution.SubscriptionPublisherException
import reactor.core.publisher.Flux
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class SentrySpringSubscriptionHandlerTest {
    @Test
    fun `reports exception`() {
        val exception = IllegalStateException("some exception")
        val scopes = mock<IScopes>()
        val exceptionReporter = mock<ExceptionReporter>()
        val parameters = mock<InstrumentationFieldFetchParameters>()
        val dataFetchingEnvironment = mock<DataFetchingEnvironment>()
        val document =
            Document
                .newDocument()
                .definition(
                    OperationDefinition
                        .newOperationDefinition()
                        .operation(OperationDefinition.Operation.QUERY)
                        .name("testQuery")
                        .build(),
                ).build()
        whenever(dataFetchingEnvironment.document).thenReturn(document)
        whenever(parameters.environment).thenReturn(dataFetchingEnvironment)
        val resultObject =
            SentrySpringSubscriptionHandler().onSubscriptionResult(
                Flux.error<Any?>(exception),
                scopes,
                exceptionReporter,
                parameters,
            )
        assertThrows<IllegalStateException> {
            (resultObject as Flux<Any?>).blockFirst()
        }

        verify(exceptionReporter).captureThrowable(
            same(exception),
            check {
                assertEquals(true, it.isSubscription)
                assertSame(scopes, it.scopes)
                assertEquals("query testQuery \n", it.query)
            },
            anyOrNull(),
        )
    }

    @Test
    fun `unwraps SubscriptionPublisherException and reports cause`() {
        val exception = IllegalStateException("some exception")
        val wrappedException = SubscriptionPublisherException(emptyList(), exception)
        val scopes = mock<IScopes>()
        val exceptionReporter = mock<ExceptionReporter>()
        val parameters = mock<InstrumentationFieldFetchParameters>()
        val dataFetchingEnvironment = mock<DataFetchingEnvironment>()
        val document =
            Document
                .newDocument()
                .definition(
                    OperationDefinition
                        .newOperationDefinition()
                        .operation(OperationDefinition.Operation.QUERY)
                        .name("testQuery")
                        .build(),
                ).build()
        whenever(dataFetchingEnvironment.document).thenReturn(document)
        whenever(parameters.environment).thenReturn(dataFetchingEnvironment)
        val resultObject =
            SentrySpringSubscriptionHandler().onSubscriptionResult(
                Flux.error<Any?>(wrappedException),
                scopes,
                exceptionReporter,
                parameters,
            )
        assertThrows<SubscriptionPublisherException> {
            (resultObject as Flux<Any?>).blockFirst()
        }

        verify(exceptionReporter).captureThrowable(
            same(exception),
            check {
                assertEquals(true, it.isSubscription)
                assertSame(scopes, it.scopes)
                assertEquals("query testQuery \n", it.query)
            },
            anyOrNull(),
        )
    }
}
