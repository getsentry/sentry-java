package io.sentry.spring.tracing

import io.sentry.IScopes
import io.sentry.Scope
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TransactionContext
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner
import java.lang.RuntimeException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(SpringRunner::class)
@SpringJUnitConfig(SentrySpanAdviceTest.Config::class)
class SentrySpanAdviceTest {

    @Autowired
    lateinit var sampleService: SampleService

    @Autowired
    lateinit var classAnnotatedSampleService: ClassAnnotatedSampleService

    @Autowired
    lateinit var classAnnotatedWithOperationSampleService: ClassAnnotatedWithOperationSampleService

    @Autowired
    lateinit var scopes: IScopes

    @BeforeTest
    fun setup() {
        whenever(scopes.options).thenReturn(SentryOptions())
    }

    @Test
    fun `when class is annotated with @SentrySpan, every method call attaches span to existing transaction`() {
        val scope = Scope(SentryOptions())
        val tx = SentryTracer(TransactionContext("aTransaction", "op"), scopes)
        scope.setTransaction(tx)

        whenever(scopes.span).thenReturn(tx)
        val result = classAnnotatedSampleService.hello()
        assertEquals(1, result)
        assertEquals(1, tx.spans.size)
        assertNull(tx.spans.first().description)
        assertEquals("auto.function.spring.advice", tx.spans.first().spanContext.origin)
        assertEquals("ClassAnnotatedSampleService.hello", tx.spans.first().operation)
    }

    @Test
    fun `when class is annotated with @SentrySpan with operation set, every method call attaches span to existing transaction`() {
        val scope = Scope(SentryOptions())
        val tx = SentryTracer(TransactionContext("aTransaction", "op"), scopes)
        scope.setTransaction(tx)

        whenever(scopes.span).thenReturn(tx)
        val result = classAnnotatedWithOperationSampleService.hello()
        assertEquals(1, result)
        assertEquals(1, tx.spans.size)
        assertNull(tx.spans.first().description)
        assertEquals("my-op", tx.spans.first().operation)
    }

    @Test
    fun `when method is annotated with @SentrySpan with properties set, attaches span to existing transaction`() {
        val scope = Scope(SentryOptions())
        val tx = SentryTracer(TransactionContext("aTransaction", "op"), scopes)
        scope.setTransaction(tx)

        whenever(scopes.span).thenReturn(tx)
        val result = sampleService.methodWithSpanDescriptionSet()
        assertEquals(1, result)
        assertEquals(1, tx.spans.size)
        assertEquals("customName", tx.spans.first().description)
        assertEquals("bean", tx.spans.first().operation)
    }

    @Test
    fun `when method is annotated with @SentrySpan without properties set, attaches span to existing transaction and sets Span description as className dot methodName`() {
        val scope = Scope(SentryOptions())
        val tx = SentryTracer(TransactionContext("aTransaction", "op"), scopes)
        scope.setTransaction(tx)

        whenever(scopes.span).thenReturn(tx)
        val result = sampleService.methodWithoutSpanDescriptionSet()
        assertEquals(2, result)
        assertEquals(1, tx.spans.size)
        assertEquals("SampleService.methodWithoutSpanDescriptionSet", tx.spans.first().operation)
        assertNull(tx.spans.first().description)
    }

    @Test
    fun `when method is annotated with @SentrySpan and returns, attached span has status OK`() {
        val scope = Scope(SentryOptions())
        val tx = SentryTracer(TransactionContext("aTransaction", "op"), scopes)
        scope.setTransaction(tx)

        whenever(scopes.span).thenReturn(tx)
        sampleService.methodWithSpanDescriptionSet()
        assertEquals(SpanStatus.OK, tx.spans.first().status)
    }

    @Test
    fun `when method is annotated with @SentrySpan and throws exception, attached span has throwable set and INTERNAL_ERROR status`() {
        val scope = Scope(SentryOptions())
        val tx = SentryTracer(TransactionContext("aTransaction", "op"), scopes)
        scope.setTransaction(tx)

        whenever(scopes.span).thenReturn(tx)
        var throwable: Throwable? = null
        try {
            sampleService.methodThrowingException()
        } catch (e: Exception) {
            throwable = e
        }
        assertEquals(SpanStatus.INTERNAL_ERROR, tx.spans.first().status)
        assertEquals(throwable, tx.spans.first().throwable)
    }

    @Test
    fun `when method is annotated with @SentrySpan and there is no active transaction, span is not created and method is executed`() {
        whenever(scopes.span).thenReturn(null)
        val result = sampleService.methodWithSpanDescriptionSet()
        assertEquals(1, result)
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(SentryTracingConfiguration::class)
    open class Config {

        @Bean
        open fun sampleService() = SampleService()

        @Bean
        open fun classAnnotatedSampleService() = ClassAnnotatedSampleService()

        @Bean
        open fun classAnnotatedWithOperationSampleService() = ClassAnnotatedWithOperationSampleService()

        @Bean
        open fun scopes(): IScopes {
            val scopes = mock<IScopes>()
            Sentry.setCurrentScopes(scopes)
            return scopes
        }
    }

    open class SampleService {

        @SentrySpan(description = "customName", operation = "bean")
        open fun methodWithSpanDescriptionSet() = 1

        @SentrySpan
        open fun methodWithoutSpanDescriptionSet() = 2

        @SentrySpan
        open fun methodThrowingException() {
            throw RuntimeException("ex")
        }
    }

    @SentrySpan
    open class ClassAnnotatedSampleService {

        open fun hello() = 1
    }

    @SentrySpan(operation = "my-op")
    open class ClassAnnotatedWithOperationSampleService {

        open fun hello() = 1
    }
}
