package io.sentry.spring.jakarta.mvc

import io.sentry.IScopes
import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.SpanDataConvention
import io.sentry.SpanStatus
import io.sentry.checkEvent
import io.sentry.checkTransaction
import io.sentry.spring.jakarta.EnableSentry
import io.sentry.spring.jakarta.SentryExceptionResolver
import io.sentry.spring.jakarta.SentrySpringFilter
import io.sentry.spring.jakarta.SentryTaskDecorator
import io.sentry.spring.jakarta.SentryUserFilter
import io.sentry.spring.jakarta.SentryUserProvider
import io.sentry.spring.jakarta.SpringSecuritySentryUserProvider
import io.sentry.spring.jakarta.exception.SentryCaptureExceptionParameter
import io.sentry.spring.jakarta.exception.SentryCaptureExceptionParameterConfiguration
import io.sentry.spring.jakarta.tracing.SentrySpanClientWebRequestFilter
import io.sentry.spring.jakarta.tracing.SentryTracingConfiguration
import io.sentry.spring.jakarta.tracing.SentryTracingFilter
import io.sentry.spring.jakarta.tracing.SentryTransaction
import io.sentry.transport.ITransport
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.awaitility.kotlin.await
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Lazy
import org.springframework.core.Ordered
import org.springframework.core.env.Environment
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.stereotype.Service
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.ExchangeFilterFunctions
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.Test

@RunWith(SpringRunner::class)
@SpringBootTest(
    classes = [App::class],

    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class SentrySpringIntegrationTest {

    companion object {
        @BeforeClass
        fun `configure awaitlity`() {
            Awaitility.setDefaultTimeout(500, TimeUnit.MILLISECONDS)
        }

        @AfterClass
        fun `reset awaitility`() {
            Awaitility.reset()
        }
    }

    @Autowired
    lateinit var transport: ITransport

    @Autowired
    lateinit var someService: SomeService

    @Autowired
    lateinit var anotherService: AnotherService

    @Autowired
    lateinit var scopes: IScopes

    @LocalServerPort
    var port: Int? = null

    @BeforeTest
    fun `reset mocks`() {
        reset(transport)
    }

    @Test
    fun `attaches request and user information to SentryEvents`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
        val headers = HttpHeaders()
        headers["X-FORWARDED-FOR"] = listOf("169.128.0.1")
        val entity = HttpEntity<Void>(headers)

        restTemplate.exchange("http://localhost:$port/hello", HttpMethod.GET, entity, Void::class.java)

        verify(transport).send(
            checkEvent { event ->
                assertThat(event.request).isNotNull()
                assertThat(event.request!!.url).isEqualTo("http://localhost:$port/hello")
                assertThat(event.user).isNotNull()
                assertThat(event.user!!.username).isEqualTo("user")
                assertThat(event.user!!.ipAddress).isEqualTo("169.128.0.1")
            },
            anyOrNull()
        )
    }

    @Test
    fun `attaches request body to SentryEvents`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
        val headers = HttpHeaders().apply {
            this.contentType = MediaType.APPLICATION_JSON
        }
        val httpEntity = HttpEntity("""{"body":"content"}""", headers)
        restTemplate.exchange("http://localhost:$port/body", HttpMethod.POST, httpEntity, Void::class.java)

        verify(transport).send(
            checkEvent { event ->
                assertThat(event.request).isNotNull()
                assertThat(event.request!!.data).isEqualTo("""{"body":"content"}""")
            },
            anyOrNull()
        )
    }

    @Test
    fun `attaches first ip address if multiple addresses exist in a header`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
        val headers = HttpHeaders()
        headers["X-FORWARDED-FOR"] = listOf("169.128.0.1, 192.168.0.1")
        val entity = HttpEntity<Void>(headers)

        restTemplate.exchange("http://localhost:$port/hello", HttpMethod.GET, entity, Void::class.java)

        verify(transport).send(
            checkEvent { event ->
                assertThat(event.user).isNotNull()
                assertThat(event.user!!.ipAddress).isEqualTo("169.128.0.1")
            },
            anyOrNull()
        )
    }

    @Test
    fun `sends events for unhandled exceptions`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/throws", String::class.java)

        verify(transport).send(
            checkEvent { event ->
                assertThat(event.exceptions).isNotNull().isNotEmpty
                val ex = event.exceptions!!.first()
                assertThat(ex.value).isEqualTo("something went wrong")
                assertThat(ex.mechanism).isNotNull()
                assertThat(ex.mechanism!!.isHandled).isFalse()
                assertThat(ex.mechanism!!.type).isEqualTo(SentryExceptionResolver.MECHANISM_TYPE)
            },
            anyOrNull()
        )
    }

    @Test
    fun `attaches transaction name to events`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/throws", String::class.java)

        verify(transport).send(
            checkEvent { event ->
                assertThat(event.transaction).isEqualTo("GET /throws")
            },
            anyOrNull()
        )
    }

    @Test
    fun `does not send events for handled exceptions`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/throws-handled", String::class.java)

        await.during(Duration.ofSeconds(2)).untilAsserted {
            verify(transport, never()).send(
                checkEvent { event ->
                    assertThat(event).isNotNull()
                },
                anyOrNull()
            )
        }
    }

    @Test
    fun `calling a method annotated with @SentryCaptureException captures exception`() {
        val exception = java.lang.RuntimeException("test exception")
        anotherService.aMethodThatTakesAnException(exception)
        verify(transport).send(
            checkEvent {
                assertThat(it.exceptions!!.first().value).isEqualTo(exception.message)
            },
            anyOrNull()
        )
    }

    @Test
    fun `calling a method annotated with @SentryCaptureException captures exception in later param`() {
        val exception = java.lang.RuntimeException("test exception")
        anotherService.aMethodThatTakesAnExceptionAsLaterParam("a", "b", exception)
        verify(transport).send(
            checkEvent {
                assertThat(it.exceptions!!.first().value).isEqualTo(exception.message)
            },
            anyOrNull()
        )
    }

    @Test
    fun `calling a method annotated with @SentryTransaction creates transaction`() {
        someService.aMethod()
        verify(transport).send(
            checkTransaction {
                assertThat(it.status).isEqualTo(SpanStatus.OK)
            },
            anyOrNull()
        )
    }

    @Test
    fun `calling a method annotated with @SentryTransaction throwing exception associates Sentry event with transaction`() {
        try {
            someService.aMethodThrowing()
        } catch (e: Exception) {
            scopes.captureException(e)
        }
        verify(transport).send(
            checkEvent {
                assertThat(it.contexts.trace).isNotNull
                assertThat(it.contexts.trace!!.operation).isEqualTo("bean")
            },
            anyOrNull()
        )
    }

    @Test
    fun `calling a method annotated with @SentryTransaction, where an inner span is created within transaction, throwing exception associates Sentry event with inner span`() {
        try {
            someService.aMethodWithInnerSpanThrowing()
        } catch (e: Exception) {
            scopes.captureException(e)
        }
        verify(transport).send(
            checkEvent {
                assertThat(it.contexts.trace).isNotNull
                assertThat(it.contexts.trace!!.operation).isEqualTo("child-op")
            },
            anyOrNull()
        )
    }

    @Test
    fun `sets user on transaction`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/hello", String::class.java)

        // transactions are sent after response is returned
        await.untilAsserted {
            verify(transport).send(
                checkTransaction { transaction ->
                    assertThat(transaction.user).isNotNull()
                    assertThat(transaction.user!!.username).isEqualTo("user")
                },
                anyOrNull()
            )
        }
    }

    @Test
    fun `scope is applied to events triggered in async methods`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/callable", String::class.java)

        await.untilAsserted {
            verify(transport).send(
                checkEvent { event ->
                    assertThat(event.message!!.formatted).isEqualTo("this message should be in the scope of the request")
                    assertThat(event.request).isNotNull()
                    assertThat(event.request!!.url).isEqualTo("http://localhost:$port/callable")
                },
                anyOrNull()
            )
        }
    }

    @Test
    fun `WebClient http request execution is turned into a span`() {
        val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

        restTemplate.getForEntity("http://localhost:$port/webClient", String::class.java)

        // transactions are sent after response is returned
        await.untilAsserted {
            verify(transport).send(
                checkTransaction { transaction ->
                    assertThat(transaction.spans).hasSize(1)
                    val span = transaction.spans.first()
                    assertThat(span.op).isEqualTo("http.client")
                    assertThat(span.description).isEqualTo("GET http://localhost:$port/hello")
                    assertThat(span.data?.get(SpanDataConvention.HTTP_STATUS_CODE_KEY)).isEqualTo(200)
                    assertThat(span.status).isEqualTo(SpanStatus.OK)
                },
                anyOrNull()
            )
        }
    }
}

@SpringBootApplication
@EnableSentry(dsn = "http://key@localhost/proj", sendDefaultPii = true, maxRequestBodySize = SentryOptions.RequestSize.MEDIUM)
@Import(SentryTracingConfiguration::class, SentryCaptureExceptionParameterConfiguration::class)
open class App {

    @Bean
    open fun mockTransportFactory(transport: ITransport): ITransportFactory {
        val factory = mock<ITransportFactory>()
        whenever(factory.create(any(), any())).thenReturn(transport)
        return factory
    }

    @Bean
    open fun mockTransport() = mock<ITransport>()

    @Bean
    open fun tracesSamplerCallback() = SentryOptions.TracesSamplerCallback {
        1.0
    }

    @Bean
    open fun springSecuritySentryUserProvider(sentryOptions: SentryOptions) = SpringSecuritySentryUserProvider(sentryOptions)

    @Bean
    open fun sentryUserFilter(scopes: IScopes, @Lazy sentryUserProviders: List<SentryUserProvider>) = FilterRegistrationBean<SentryUserFilter>().apply {
        this.filter = SentryUserFilter(scopes, sentryUserProviders)
        this.order = Ordered.LOWEST_PRECEDENCE
    }

    @Bean
    open fun sentrySpringFilter(scopes: IScopes) = FilterRegistrationBean<SentrySpringFilter>().apply {
        this.filter = SentrySpringFilter(scopes)
        this.order = Ordered.HIGHEST_PRECEDENCE
    }

    @Bean
    open fun sentryTracingFilter(scopes: IScopes) = FilterRegistrationBean<SentryTracingFilter>().apply {
        this.filter = SentryTracingFilter(scopes)
        this.order = Ordered.HIGHEST_PRECEDENCE + 1 // must run after SentrySpringFilter
    }

    @Bean
    open fun sentryTaskDecorator() = SentryTaskDecorator()

    @Bean
    open fun webClient(scopes: IScopes): WebClient {
        return WebClient.builder()
            .filter(
                ExchangeFilterFunctions
                    .basicAuthentication("user", "password")
            )
            .filter(SentrySpanClientWebRequestFilter(scopes)).build()
    }
}

@Service
open class AnotherService {
    @SentryCaptureExceptionParameter
    open fun aMethodThatTakesAnException(e: Exception) {}

    @SentryCaptureExceptionParameter
    open fun aMethodThatTakesAnExceptionAsLaterParam(a: String, b: String, e: Exception) {}
}

@Service
open class SomeService {

    @SentryTransaction(operation = "bean")
    open fun aMethod() {
        Thread.sleep(100)
    }

    @SentryTransaction(operation = "bean")
    open fun aMethodThrowing() {
        throw RuntimeException("oops")
    }

    @SentryTransaction(operation = "bean")
    open fun aMethodWithInnerSpanThrowing() {
        val span = Sentry.getSpan()!!.startChild("child-op")
        try {
            throw RuntimeException("oops")
        } catch (e: Exception) {
            span.status = SpanStatus.INTERNAL_ERROR
            span.throwable = e
            throw e
        } finally {
            span.finish()
        }
    }
}

@RestController
class HelloController(private val webClient: WebClient, private val env: Environment) {

    @GetMapping("/hello")
    fun hello(): String {
        Sentry.captureMessage("hello")
        return "hello"
    }

    @PostMapping("/body")
    fun body() {
        Sentry.captureMessage("body")
    }

    @GetMapping("/throws")
    fun throws() {
        throw RuntimeException("something went wrong")
    }

    @GetMapping("/throws-handled")
    fun throwsHandled() {
        throw CustomException("handled exception")
    }

    @GetMapping("/callable")
    fun callable(): Callable<String> {
        return Callable {
            Sentry.captureMessage("this message should be in the scope of the request")
            "from callable"
        }
    }

    @GetMapping("/webClient")
    fun webClient(): String? {
        return webClient.get().uri("http://localhost:${env.getProperty("local.server.port")}/hello").retrieve().bodyToMono(String::class.java).block()
    }
}

class CustomException(message: String) : RuntimeException(message)

@ControllerAdvice
class ExceptionHandlers {

    @ExceptionHandler(CustomException::class)
    fun handle(e: CustomException) = ResponseEntity.badRequest().build<Void>()
}

@Configuration
open class SecurityConfiguration {

    @Bean
    open fun userDetailsService(): InMemoryUserDetailsManager {
        val encoder: PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        val user: UserDetails = User
            .builder()
            .passwordEncoder { rawPassword -> encoder.encode(rawPassword) }
            .username("user")
            .password("password")
            .roles("USER")
            .build()
        return InMemoryUserDetailsManager(user)
    }

    @Bean
    @Throws(Exception::class)
    open fun filterChain(http: HttpSecurity): SecurityFilterChain {
        http.csrf().disable()
            .authorizeRequests().anyRequest().authenticated()
            .and()
            .httpBasic()

        return http.build()
    }
}
