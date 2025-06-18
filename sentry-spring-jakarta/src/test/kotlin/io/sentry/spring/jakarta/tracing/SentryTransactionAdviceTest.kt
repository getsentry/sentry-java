package io.sentry.spring.jakarta.tracing

import io.sentry.IScopes
import io.sentry.ISentryLifecycleToken
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SentryTracer
import io.sentry.SpanStatus
import io.sentry.TraceContext
import io.sentry.TransactionContext
import io.sentry.TransactionOptions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

@RunWith(SpringRunner::class)
@SpringJUnitConfig(SentryTransactionAdviceTest.Config::class)
class SentryTransactionAdviceTest {
    @Autowired
    lateinit var sampleService: SampleService

    @Autowired
    lateinit var classAnnotatedSampleService: ClassAnnotatedSampleService

    @Autowired
    lateinit var classAnnotatedWithOperationSampleService: ClassAnnotatedWithOperationSampleService

    @Autowired
    lateinit var scopes: IScopes

    val lifecycleToken = mock<ISentryLifecycleToken>()

    @BeforeTest
    fun setup() {
        reset(scopes)
        whenever(
            scopes.startTransaction(
                any(),
                check<TransactionOptions> {
                    assertTrue(it.isBindToScope)
                    assertThat(it.origin).isEqualTo("auto.function.spring_jakarta.advice")
                },
            ),
        ).thenAnswer { SentryTracer(it.arguments[0] as TransactionContext, scopes) }
        whenever(scopes.options).thenReturn(
            SentryOptions().apply {
                dsn = "https://key@sentry.io/proj"
            },
        )
        whenever(scopes.forkedScopes(any())).thenReturn(scopes)
        whenever(scopes.makeCurrent()).thenReturn(lifecycleToken)
    }

    @Test
    fun `creates transaction around method annotated with @SentryTransaction`() {
        sampleService.methodWithTransactionNameSet()
        verify(scopes).captureTransaction(
            check {
                assertThat(it.transaction).isEqualTo("customName")
                assertThat(it.contexts.trace!!.operation).isEqualTo("bean")
                assertThat(it.status).isEqualTo(SpanStatus.OK)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `when method annotated with @SentryTransaction throws exception, sets error status on transaction`() {
        assertThrows<RuntimeException> { sampleService.methodThrowingException() }
        verify(scopes).captureTransaction(
            check {
                assertThat(it.status).isEqualTo(SpanStatus.INTERNAL_ERROR)
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `when @SentryTransaction has no name set, sets transaction name as className dot methodName`() {
        sampleService.methodWithoutTransactionNameSet()
        verify(scopes).captureTransaction(
            check {
                assertThat(it.transaction).isEqualTo("SampleService.methodWithoutTransactionNameSet")
                assertThat(it.contexts.trace!!.operation).isEqualTo("op")
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `when transaction is already active, does not start new transaction`() {
        whenever(scopes.options).thenReturn(SentryOptions())
        whenever(scopes.span).then { SentryTracer(TransactionContext("aTransaction", "op"), scopes) }

        sampleService.methodWithTransactionNameSet()

        verify(scopes, times(0)).captureTransaction(any(), any<TraceContext>())
    }

    @Test
    fun `creates transaction around method in class annotated with @SentryTransaction`() {
        classAnnotatedSampleService.hello()
        verify(scopes).captureTransaction(
            check {
                assertThat(it.transaction).isEqualTo("ClassAnnotatedSampleService.hello")
                assertThat(it.contexts.trace!!.operation).isEqualTo("op")
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `creates transaction with operation set around method in class annotated with @SentryTransaction`() {
        classAnnotatedWithOperationSampleService.hello()
        verify(scopes).captureTransaction(
            check {
                assertThat(it.transaction).isEqualTo("ClassAnnotatedWithOperationSampleService.hello")
                assertThat(it.contexts.trace!!.operation).isEqualTo("my-op")
            },
            anyOrNull<TraceContext>(),
            anyOrNull(),
            anyOrNull(),
        )
    }

    @Test
    fun `pushes the scope when advice starts`() {
        classAnnotatedSampleService.hello()
        verify(scopes).forkedScopes(any())
        verify(scopes).makeCurrent()
    }

    @Test
    fun `pops the scope when advice finishes`() {
        classAnnotatedSampleService.hello()
        verify(lifecycleToken).close()
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
        @SentryTransaction(name = "customName", operation = "bean")
        open fun methodWithTransactionNameSet() = Unit

        @SentryTransaction(operation = "op")
        open fun methodWithoutTransactionNameSet() = Unit

        @SentryTransaction(operation = "op")
        open fun methodThrowingException(): Nothing = throw RuntimeException()
    }

    @SentryTransaction(operation = "op")
    open class ClassAnnotatedSampleService {
        open fun hello() = Unit
    }

    @SentryTransaction(operation = "my-op")
    open class ClassAnnotatedWithOperationSampleService {
        open fun hello() = Unit
    }
}
