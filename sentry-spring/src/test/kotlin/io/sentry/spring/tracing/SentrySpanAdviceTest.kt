package io.sentry.spring.tracing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.SentryTransaction
import io.sentry.TransactionContexts
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.aopalliance.aop.Advice
import org.junit.runner.RunWith
import org.springframework.aop.Advisor
import org.springframework.aop.Pointcut
import org.springframework.aop.support.ComposablePointcut
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringJUnitConfig(SentrySpanAdviceTest.Config::class)
class SentrySpanAdviceTest {

    @Autowired
    lateinit var sampleService: SampleService

    @Autowired
    lateinit var serviceAnnotatedComponent: ServiceAnnotatedComponent

    @Autowired
    lateinit var hub: IHub

    @BeforeTest
    fun setup() {
        whenever(hub.startTransaction(any())).thenAnswer { SentryTransaction(it.arguments[0] as String, TransactionContexts(), hub) }
    }

    @Test
    fun `when method is annotated with @SentrySpan with properties set, attaches span to existing transaction`() {
        val scope = Scope(SentryOptions())
        val tx = SentryTransaction("aTransaction", TransactionContexts(), hub)
        scope.setTransaction(tx)

        whenever(hub.configureScope(any())).thenAnswer {
            (it.arguments[0] as ScopeCallback).run(scope)
        }
        val result = sampleService.methodWithSpanDescriptionSet()
        assertEquals(1, result)
        assertEquals(1, tx.spans.size)
        assertEquals("customName", tx.spans.first().description)
        assertEquals("bean", tx.spans.first().op)
    }

    @Test
    fun `when method is annotated with @SentrySpan without properties set, attaches span to existing transaction and sets Span description as className dot methodName`() {
        val scope = Scope(SentryOptions())
        val tx = SentryTransaction("aTransaction", TransactionContexts(), hub)
        scope.setTransaction(tx)

        whenever(hub.configureScope(any())).thenAnswer {
            (it.arguments[0] as ScopeCallback).run(scope)
        }
        val result = sampleService.methodWithoutSpanDescriptionSet()
        assertEquals(2, result)
        assertEquals(1, tx.spans.size)
        assertEquals("SampleService.methodWithoutSpanDescriptionSet", tx.spans.first().description)
        assertNull(tx.spans.first().op)
    }

    @Test
    fun `when method is annotated with @SentrySpan and there is no active transaction, span is not created and method is executed`() {
        val scope = Scope(SentryOptions())
        whenever(hub.configureScope(any())).thenAnswer {
            (it.arguments[0] as ScopeCallback).run(scope)
        }
        val result = sampleService.methodWithSpanDescriptionSet()
        assertEquals(1, result)
    }

    @Test
    fun `when pointcut matching method is not annotated with @SentrySpan, attaches span to existing transaction and sets Span description as className dot methodName`() {
        val scope = Scope(SentryOptions())
        val tx = SentryTransaction("aTransaction", TransactionContexts(), hub)
        scope.setTransaction(tx)

        whenever(hub.configureScope(any())).thenAnswer {
            (it.arguments[0] as ScopeCallback).run(scope)
        }
        val result = serviceAnnotatedComponent.hello()
        assertEquals(10, result)
        assertEquals(1, tx.spans.size)
        assertEquals("ServiceAnnotatedComponent.hello", tx.spans.first().description)
        assertNull(tx.spans.first().op)
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(ServiceAnnotatedComponent::class)
    open class Config {

        @Bean
        open fun sampleService() = SampleService()

        @Bean
        open fun hub() = mock<IHub>()

        @Bean
        open fun sentrySpanPointcut(): Pointcut {
            return ComposablePointcut(AnnotationMatchingPointcut(Component::class.java))
                .union(AnnotationMatchingPointcut(Service::class.java))
                .union(AnnotationMatchingPointcut(null, SentrySpan::class.java))
        }

        @Bean
        open fun sentrySpanAdvice(hub: IHub): Advice {
            return SentrySpanAdvice(hub)
        }

        @Bean
        open fun sentrySpanAdvisor(hub: IHub): Advisor {
            return DefaultPointcutAdvisor(sentrySpanPointcut(), sentrySpanAdvice(hub))
        }
    }

    open class SampleService {

        @SentrySpan(description = "customName", op = "bean")
        open fun methodWithSpanDescriptionSet() = 1

        @SentrySpan
        open fun methodWithoutSpanDescriptionSet() = 2
    }

    @Service
    open class ServiceAnnotatedComponent {

        open fun hello() = 10
    }
}
