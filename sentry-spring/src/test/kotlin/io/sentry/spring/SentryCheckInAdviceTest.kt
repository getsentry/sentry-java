package io.sentry.spring

import io.sentry.CheckIn
import io.sentry.CheckInStatus
import io.sentry.IHub
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.protocol.SentryId
import io.sentry.spring.checkin.SentryCheckIn
import io.sentry.spring.checkin.SentryCheckInAdviceConfiguration
import io.sentry.spring.checkin.SentryCheckInPointcutConfiguration
import org.junit.jupiter.api.assertThrows
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy
import org.springframework.context.annotation.Import
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.util.StringValueResolver
import kotlin.RuntimeException
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(SpringRunner::class)
@SpringJUnitConfig(SentryCheckInAdviceTest.Config::class)
@TestPropertySource(properties = ["my.cron.slug = mypropertycronslug"])
class SentryCheckInAdviceTest {

    @Autowired
    lateinit var sampleService: SampleService

    @Autowired
    lateinit var sampleServiceNoSlug: SampleServiceNoSlug

    @Autowired
    lateinit var sampleServiceHeartbeat: SampleServiceHeartbeat

    @Autowired
    lateinit var sampleServiceSpringProperties: SampleServiceSpringProperties

    @Autowired
    lateinit var hub: IHub

    @BeforeTest
    fun setup() {
        reset(hub)
        whenever(hub.options).thenReturn(SentryOptions())
    }

    @Test
    fun `when method is annotated with @SentryCheckIn, every method call creates two check-ins`() {
        val checkInId = SentryId()
        val checkInCaptor = argumentCaptor<CheckIn>()
        whenever(hub.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
        val result = sampleService.hello()
        assertEquals(1, result)
        assertEquals(2, checkInCaptor.allValues.size)

        val inProgressCheckIn = checkInCaptor.firstValue
        assertEquals("monitor_slug_1", inProgressCheckIn.monitorSlug)
        assertEquals(CheckInStatus.IN_PROGRESS.apiName(), inProgressCheckIn.status)

        val doneCheckIn = checkInCaptor.lastValue
        assertEquals("monitor_slug_1", doneCheckIn.monitorSlug)
        assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)

        val order = inOrder(hub)
        order.verify(hub).pushScope()
        order.verify(hub, times(2)).captureCheckIn(any())
        order.verify(hub).popScope()
    }

    @Test
    fun `when method is annotated with @SentryCheckIn, every method call creates two check-ins error`() {
        val checkInId = SentryId()
        val checkInCaptor = argumentCaptor<CheckIn>()
        whenever(hub.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
        assertThrows<RuntimeException> {
            sampleService.oops()
        }
        assertEquals(2, checkInCaptor.allValues.size)

        val inProgressCheckIn = checkInCaptor.firstValue
        assertEquals("monitor_slug_1e", inProgressCheckIn.monitorSlug)
        assertEquals(CheckInStatus.IN_PROGRESS.apiName(), inProgressCheckIn.status)

        val doneCheckIn = checkInCaptor.lastValue
        assertEquals("monitor_slug_1e", doneCheckIn.monitorSlug)
        assertEquals(CheckInStatus.ERROR.apiName(), doneCheckIn.status)

        val order = inOrder(hub)
        order.verify(hub).pushScope()
        order.verify(hub, times(2)).captureCheckIn(any())
        order.verify(hub).popScope()
    }

    @Test
    fun `when method is annotated with @SentryCheckIn and heartbeat only, every method call creates only one check-in at the end`() {
        val checkInId = SentryId()
        val checkInCaptor = argumentCaptor<CheckIn>()
        whenever(hub.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
        val result = sampleServiceHeartbeat.hello()
        assertEquals(1, result)
        assertEquals(1, checkInCaptor.allValues.size)

        val doneCheckIn = checkInCaptor.lastValue
        assertEquals("monitor_slug_2", doneCheckIn.monitorSlug)
        assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
        assertNotNull(doneCheckIn.duration)

        val order = inOrder(hub)
        order.verify(hub).pushScope()
        order.verify(hub).captureCheckIn(any())
        order.verify(hub).popScope()
    }

    @Test
    fun `when method is annotated with @SentryCheckIn and heartbeat only, every method call creates only one check-in at the end with error`() {
        val checkInId = SentryId()
        val checkInCaptor = argumentCaptor<CheckIn>()
        whenever(hub.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
        assertThrows<RuntimeException> {
            sampleServiceHeartbeat.oops()
        }
        assertEquals(1, checkInCaptor.allValues.size)

        val doneCheckIn = checkInCaptor.lastValue
        assertEquals("monitor_slug_2e", doneCheckIn.monitorSlug)
        assertEquals(CheckInStatus.ERROR.apiName(), doneCheckIn.status)
        assertNotNull(doneCheckIn.duration)

        val order = inOrder(hub)
        order.verify(hub).pushScope()
        order.verify(hub).captureCheckIn(any())
        order.verify(hub).popScope()
    }

    @Test
    fun `when method is annotated with @SentryCheckIn but slug is missing, does not create check-in`() {
        val checkInId = SentryId()
        val checkInCaptor = argumentCaptor<CheckIn>()
        whenever(hub.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
        val result = sampleServiceNoSlug.hello()
        assertEquals(1, result)
        assertEquals(0, checkInCaptor.allValues.size)

        verify(hub, never()).pushScope()
        verify(hub, never()).captureCheckIn(any())
        verify(hub, never()).popScope()
    }

    @Test
    fun `when @SentryCheckIn is passed a spring property it is resolved correctly`() {
        val checkInId = SentryId()
        val checkInCaptor = argumentCaptor<CheckIn>()
        whenever(hub.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
        val result = sampleServiceSpringProperties.hello()
        assertEquals(1, result)
        assertEquals(1, checkInCaptor.allValues.size)

        val doneCheckIn = checkInCaptor.lastValue
        assertEquals("mypropertycronslug", doneCheckIn.monitorSlug)
        assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
        assertNotNull(doneCheckIn.duration)

        val order = inOrder(hub)
        order.verify(hub).pushScope()
        order.verify(hub).captureCheckIn(any())
        order.verify(hub).popScope()
    }

    @Test
    fun `when @SentryCheckIn is passed a spring property that does not exist, raw value is used`() {
        val checkInId = SentryId()
        val checkInCaptor = argumentCaptor<CheckIn>()
        whenever(hub.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
        val result = sampleServiceSpringProperties.helloUnresolvedProperty()
        assertEquals(1, result)
        assertEquals(1, checkInCaptor.allValues.size)

        val doneCheckIn = checkInCaptor.lastValue
        assertEquals("\${my.cron.wrong.property}", doneCheckIn.monitorSlug)
        assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
        assertNotNull(doneCheckIn.duration)

        val order = inOrder(hub)
        order.verify(hub).pushScope()
        order.verify(hub).captureCheckIn(any())
        order.verify(hub).popScope()
    }

    @Test
    fun `when @SentryCheckIn is passed a spring property that causes an exception, raw value is used`() {
        val checkInId = SentryId()
        val checkInCaptor = argumentCaptor<CheckIn>()
        whenever(hub.captureCheckIn(checkInCaptor.capture())).thenReturn(checkInId)
        val result = sampleServiceSpringProperties.helloExceptionProperty()
        assertEquals(1, result)
        assertEquals(1, checkInCaptor.allValues.size)

        val doneCheckIn = checkInCaptor.lastValue
        assertEquals("\${my.cron.wrong.property}", doneCheckIn.monitorSlug)
        assertEquals(CheckInStatus.OK.apiName(), doneCheckIn.status)
        assertNotNull(doneCheckIn.duration)

        val order = inOrder(hub)
        order.verify(hub).pushScope()
        order.verify(hub).captureCheckIn(any())
        order.verify(hub).popScope()
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(SentryCheckInAdviceConfiguration::class, SentryCheckInPointcutConfiguration::class)
    open class Config {

        @Bean
        open fun sampleService() = SampleService()

        @Bean
        open fun sampleServiceNoSlug() = SampleServiceNoSlug()

        @Bean
        open fun sampleServiceHeartbeat() = SampleServiceHeartbeat()

        @Bean
        open fun sampleServiceSpringProperties() = SampleServiceSpringProperties()

        @Bean
        open fun hub(): IHub {
            val hub = mock<IHub>()
            Sentry.setCurrentHub(hub)
            return hub
        }

        companion object {
            @Bean
            @JvmStatic
            fun propertySourcesPlaceholderConfigurer() = MyPropertyPlaceholderConfigurer()
        }
    }

    open class SampleService {

        @SentryCheckIn("monitor_slug_1")
        open fun hello() = 1

        @SentryCheckIn("monitor_slug_1e")
        open fun oops() {
            throw RuntimeException("thrown on purpose")
        }
    }

    open class SampleServiceNoSlug {

        @SentryCheckIn
        open fun hello() = 1
    }

    open class SampleServiceHeartbeat {

        @SentryCheckIn(monitorSlug = "monitor_slug_2", heartbeat = true)
        open fun hello() = 1

        @SentryCheckIn(monitorSlug = "monitor_slug_2e", heartbeat = true)
        open fun oops() {
            throw RuntimeException("thrown on purpose")
        }
    }

    open class SampleServiceSpringProperties {

        @SentryCheckIn("\${my.cron.slug}", heartbeat = true)
        open fun hello() = 1

        @SentryCheckIn("\${my.cron.unresolved.property}", heartbeat = true)
        open fun helloUnresolvedProperty() = 1

        @SentryCheckIn("\${my.cron.exception.property}", heartbeat = true)
        open fun helloExceptionProperty() = 1
    }


    class MyPropertyPlaceholderConfigurer : PropertySourcesPlaceholderConfigurer() {

        override fun doProcessProperties(
            beanFactoryToProcess: ConfigurableListableBeanFactory,
            valueResolver: StringValueResolver
        ) {
            val wrappedResolver = StringValueResolver { strVal: String ->
                if("\${my.cron.exception.property}".equals(strVal)) {
                    throw IllegalArgumentException("Cannot resolve property: $strVal")
                } else {
                    valueResolver.resolveStringValue(strVal)
                }
            }
            super.doProcessProperties(beanFactoryToProcess, wrappedResolver)
        }
    }
}
