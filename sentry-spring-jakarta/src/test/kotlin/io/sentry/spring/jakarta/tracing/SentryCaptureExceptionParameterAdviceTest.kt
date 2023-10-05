package io.sentry.spring.jakarta.tracing

import io.sentry.IHub
import io.sentry.exception.ExceptionMechanismException
import io.sentry.spring.jakarta.exception.SentryCaptureExceptionParameter
import io.sentry.spring.jakarta.exception.SentryCaptureExceptionParameterConfiguration
import org.junit.runner.RunWith
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(SpringRunner::class)
@SpringJUnitConfig(SentryCaptureExceptionParameterAdviceTest.Config::class)
class SentryCaptureExceptionParameterAdviceTest {

    @Autowired
    lateinit var sampleService: SampleService

    @Autowired
    lateinit var hub: IHub

    @BeforeTest
    fun setup() {
        reset(hub)
    }

    @Test
    fun `captures exception passed to method annotated with @SentryCaptureException`() {
        val exception = RuntimeException("test exception")
        sampleService.methodTakingAnException(exception)
        verify(hub).captureException(
            check {
                assertTrue(it is ExceptionMechanismException)
                val mechanismException = it as ExceptionMechanismException
                assertEquals(exception, mechanismException.throwable)
                assertEquals("SentrySpring6CaptureExceptionParameterAdvice", mechanismException.exceptionMechanism.type)
            }
        )
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(SentryCaptureExceptionParameterConfiguration::class)
    open class Config {

        @Bean
        open fun sampleService() = SampleService()

        @Bean
        open fun hub() = mock<IHub>()
    }

    open class SampleService {

        @SentryCaptureExceptionParameter
        open fun methodTakingAnException(e: Exception) = Unit
    }
}
