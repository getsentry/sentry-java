package io.sentry.spring.webflux

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.anyOrNull
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.checkEvent
import io.sentry.transport.ITransport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.http.ResponseEntity
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(SpringRunner::class)
@SpringBootTest(
    classes = [App::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.web-application-type=reactive"]
)
class SentryWebfluxIntegrationTest {

    @Autowired
    lateinit var transport: ITransport

    @LocalServerPort
    var port: Int? = null

    private val testClient = WebTestClient.bindToServer().build()

    @Before
    fun `reset mocks`() {
        reset(transport)
    }

    @Test
    fun `attaches request information to SentryEvents`() {
        testClient.get()
            .uri("http://localhost:$port/hello?param=value")
            .exchange()
            .expectStatus()
            .isOk

        verify(transport).send(
            checkEvent { event ->
                assertNotNull(event.request) {
                    assertEquals("http://localhost:$port/hello?param=value", it.url)
                    assertEquals("GET", it.method)
                    assertEquals("param=value", it.queryString)
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `sends events for unhandled exceptions`() {
        testClient.get()
            .uri("http://localhost:$port/throws")
            .exchange()
            .expectStatus()
            .is5xxServerError

        verify(transport).send(
            checkEvent { event ->
                assertEquals("GET /throws", event.transaction)
                assertNotNull(event.exceptions) {
                    val ex = it.first()
                    assertEquals("something went wrong", ex.value)
                    assertNotNull(ex.mechanism) {
                        assertThat(it.isHandled).isFalse()
                    }
                }
            },
            anyOrNull()
        )
    }

    @Test
    fun `does not send events for handled exceptions`() {
        testClient.get()
            .uri("http://localhost:$port/throws-handled")
            .exchange()
            .expectStatus()
            .isBadRequest

        await.during(Duration.ofSeconds(2)).untilAsserted {
            verify(transport, never()).send(
                checkEvent { event ->
                    assertNotNull(event)
                },
                anyOrNull()
            )
        }
    }
}

@SpringBootApplication(exclude = [ReactiveSecurityAutoConfiguration::class, SecurityAutoConfiguration::class])
open class App {

    private val transport = mock<ITransport>()

    @Bean
    open fun mockTransportFactory(): ITransportFactory {
        val factory = mock<ITransportFactory>()
        whenever(factory.create(any(), any())).thenReturn(transport)
        return factory
    }

    @Bean
    open fun mockTransport() = transport

    @Bean
    open fun hub() = HubAdapter.getInstance()

    @Bean
    open fun sentryFilter(hub: IHub) = SentryWebFilter(hub)

    @Bean
    open fun sentryWebExceptionHandler(hub: IHub) = SentryWebExceptionHandler(hub)

    @Bean
    open fun sentryScheduleHookRegistrar() = ApplicationRunner {
        Schedulers.onScheduleHook("sentry", SentryScheduleHook())
    }

    @Bean
    open fun sentryInitializer(transportFactory: ITransportFactory) = ApplicationRunner {
        Sentry.init {
            it.dsn = "http://key@localhost/proj"
            it.setDebug(true)
            it.setTransportFactory(transportFactory)
        }
    }
}

@RestController
class HelloController {

    @GetMapping("/hello")
    fun hello(): Mono<Void> {
        Sentry.captureMessage("hello")
        return Mono.empty<Void>()
    }

    @GetMapping("/throws")
    fun throws() {
        throw RuntimeException("something went wrong")
    }

    @GetMapping("/throws-handled")
    fun throwsHandled() {
        throw CustomException("handled exception")
    }
}

class CustomException(message: String) : RuntimeException(message)

@ControllerAdvice
class ExceptionHandlers {

    @ExceptionHandler(CustomException::class)
    fun handle(e: CustomException) = ResponseEntity.badRequest().build<Void>()
}
