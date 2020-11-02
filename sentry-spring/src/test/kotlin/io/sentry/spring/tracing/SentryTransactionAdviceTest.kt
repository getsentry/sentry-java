package io.sentry.spring.tracing

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.check
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.IHub
import io.sentry.Scope
import io.sentry.ScopeCallback
import io.sentry.SentryOptions
import io.sentry.TransactionContexts
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.aopalliance.aop.Advice
import org.assertj.core.api.Assertions.assertThat
import org.junit.runner.RunWith
import org.springframework.aop.Advisor
import org.springframework.aop.Pointcut
import org.springframework.aop.support.DefaultPointcutAdvisor
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner::class)
@SpringJUnitConfig(SentryTransactionAdviceTest.Config::class)
class SentryTransactionAdviceTest {

    @Autowired
    lateinit var sampleService: SampleService

    @Autowired
    lateinit var hub: IHub

    @BeforeTest
    fun setup() {
        whenever(hub.startTransaction(any())).thenAnswer { io.sentry.SentryTransaction(it.arguments[0] as String, TransactionContexts(), hub) }
    }

    @Test
    fun `creates transaction around method annotated with @SentryTransaction`() {
        sampleService.methodWithTransactionNameSet()
        verify(hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("customName")
            assertThat(it.contexts.traceContext.op).isEqualTo("bean")
        }, eq(null))
    }

    @Test
    fun `when @SentryTransaction has no name set, sets transaction name as className dot methodName`() {
        sampleService.methodWithoutTransactionNameSet()
        verify(hub).captureTransaction(check {
            assertThat(it.transaction).isEqualTo("SampleService.methodWithoutTransactionNameSet")
            assertThat(it.contexts.traceContext.op).isNull()
        }, eq(null))
    }

    @Test
    fun `when transaction is already active, does not start new transaction`() {
        val scope = Scope(SentryOptions())
        scope.setTransaction(io.sentry.SentryTransaction("aTransaction", TransactionContexts(), hub))

        whenever(hub.configureScope(any())).thenAnswer {
            (it.arguments[0] as ScopeCallback).run(scope)
        }

        sampleService.methodWithTransactionNameSet()
        verify(hub, times(0)).captureTransaction(any(), any())
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    open class Config {

        @Bean
        open fun sampleService() = SampleService()

        @Bean
        open fun hub() = mock<IHub>()

        @Bean
        open fun sentryTransactionPointcut(): Pointcut {
            return AnnotationMatchingPointcut(null, SentryTransaction::class.java)
        }

        @Bean
        open fun sentryTransactionAdvice(hub: IHub): Advice {
            return SentryTransactionAdvice(hub)
        }

        @Bean
        open fun sentryTransactionAdvisor(hub: IHub): Advisor {
            return DefaultPointcutAdvisor(sentryTransactionPointcut(), sentryTransactionAdvice(hub))
        }
    }

    open class SampleService {

        @SentryTransaction(name = "customName", op = "bean")
        open fun methodWithTransactionNameSet() = Unit

        @SentryTransaction
        open fun methodWithoutTransactionNameSet() = Unit
    }
}
