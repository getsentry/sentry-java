package io.sentry.spring.webflux

import io.sentry.IScopes
import io.sentry.ITransportFactory
import io.sentry.ScopesAdapter
import io.sentry.Sentry
import io.sentry.checkEvent
import io.sentry.checkTransaction
import io.sentry.test.initForTest
import io.sentry.transport.ITransport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    @BeforeTest
    fun `reset mocks`() {
        reset(transport)
    }

    @Test
    fun `attaches request information to SentryEvents`() {
        testClient.get()
            .uri("http://localhost:$port/hello?param=value#top")
            .exchange()
            .expectStatus()
            .isOk

        verify(transport).send(
            checkEvent { event ->
                assertNotNull(event.request) {
                    assertEquals("http://localhost:$port/hello", it.url)
                    assertEquals("GET", it.method)
                    assertEquals("param=value", it.queryString)
                    assertNull(it.fragment)
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
                    val ex = it.last()
                    assertEquals("something went wrong", ex.value)
                    assertNotNull(ex.mechanism) {
                        assertThat(it.isHandled).isFalse()
                        assertThat(it.type).isEqualTo(SentryWebExceptionHandler.MECHANISM_TYPE)
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

    @Test
    fun `sends transaction`() {
        testClient.get()
            .uri("http://localhost:$port/hello?param=value#top")
            .exchange()
            .expectStatus()
            .isOk

        verify(transport).send(
            checkTransaction { event ->
                assertEquals("GET /hello", event.transaction)
            },
            anyOrNull()
        )
    }
}

@SpringBootApplication(exclude = [ReactiveSecurityAutoConfiguration::class, SecurityAutoConfiguration::class])
open class App {

    private val transport = mock<ITransport>().also {
        whenever(it.isHealthy).thenReturn(true)
    }

    @Bean
    open fun mockTransportFactory(): ITransportFactory {
        val factory = mock<ITransportFactory>()
        whenever(factory.create(any(), any())).thenReturn(transport)
        return factory
    }

    @Bean
    open fun mockTransport() = transport

    @Bean
    open fun scopes() = ScopesAdapter.getInstance()

    @Bean
    open fun sentryFilter(scopes: IScopes) = SentryWebFilter(scopes)

    @Bean
    open fun sentryWebExceptionHandler(scopes: IScopes) = SentryWebExceptionHandler(scopes)

    @Bean
    open fun sentryScheduleHookRegistrar() = ApplicationRunner {
        Schedulers.onScheduleHook("sentry", SentryScheduleHook())
    }

    @Bean
    open fun sentryInitializer(transportFactory: ITransportFactory) = ApplicationRunner {
        initForTest {
            it.dsn = "http://key@localhost/proj"
            it.setDebug(true)
            it.setTransportFactory(transportFactory)
            it.tracesSampleRate = 1.0
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
