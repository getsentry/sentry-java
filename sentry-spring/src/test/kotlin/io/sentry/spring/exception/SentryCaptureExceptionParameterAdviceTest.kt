package io.sentry.spring.exception

import io.sentry.Hint
import io.sentry.IHub
import io.sentry.Sentry
import io.sentry.exception.ExceptionMechanismException
import org.junit.runner.RunWith
import org.mockito.kotlin.any
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
                assertEquals(exception, it.throwable)
                assertEquals("SentrySpring5CaptureExceptionParameterAdvice", it.exceptionMechanism.type)
            },
            any<Hint>()
        )
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(SentryCaptureExceptionParameterConfiguration::class)
    open class Config {

        @Bean
        open fun sampleService() = SampleService()

        @Bean
        open fun hub(): IHub {
            val hub = mock<IHub>()
            Sentry.setCurrentHub(hub)
            return hub
        }
    }

    open class SampleService {

        @SentryCaptureExceptionParameter
        open fun methodTakingAnException(e: Exception) = Unit
    }
}
