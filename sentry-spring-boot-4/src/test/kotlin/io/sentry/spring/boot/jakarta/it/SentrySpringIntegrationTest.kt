package io.sentry.spring.boot.jakarta.it

import io.sentry.DefaultSpanFactory
import io.sentry.IScopes
import io.sentry.ITransportFactory
import io.sentry.Sentry
import io.sentry.SentryOpenTelemetryMode
import io.sentry.SentryOptions
import io.sentry.checkEvent
import io.sentry.checkTransaction
import io.sentry.spring.jakarta.tracing.SentrySpan
import io.sentry.transport.ITransport
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.assertj.core.api.Assertions.assertThat
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.web.server.test.LocalServerPort
import org.springframework.boot.web.server.test.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [App::class],
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties =
    [
      "sentry.dsn=http://key@localhost/proj",
      "sentry.send-default-pii=true",
      "sentry.traces-sample-rate=1.0",
      "sentry.max-request-body-size=medium",
      "sentry.enable-backpressure-handling=false",
    ],
)
class SentrySpringIntegrationTest {

  @Autowired lateinit var transport: ITransport

  @MockitoSpyBean lateinit var scopes: IScopes

  @LocalServerPort var port: Int? = null

  @BeforeTest
  fun reset() {
    reset(transport)
  }

  @Test
  fun `attaches request and user information to SentryEvents`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
    val headers = HttpHeaders()
    headers.put("X-FORWARDED-FOR", listOf("169.128.0.1"))
    val entity = HttpEntity<Void>(headers)

    restTemplate.exchange("http://localhost:$port/hello", HttpMethod.GET, entity, Void::class.java)

    verify(transport)
      .send(
        checkEvent { event ->
          assertThat(event.request).isNotNull()
          assertThat(event.request!!.url).isEqualTo("http://localhost:$port/hello")
          assertThat(event.user).isNotNull()
          assertThat(event.user!!.username).isEqualTo("user")
          assertThat(event.user!!.ipAddress).isEqualTo("169.128.0.1")
        },
        anyOrNull(),
      )
  }

  @Test
  fun `attaches request body to SentryEvents`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
    val headers = HttpHeaders().apply { this.contentType = MediaType.APPLICATION_JSON }
    val httpEntity = HttpEntity("""{"body":"content"}""", headers)
    restTemplate.exchange(
      "http://localhost:$port/bodyAsParam",
      HttpMethod.POST,
      httpEntity,
      Void::class.java,
    )

    verify(transport)
      .send(
        checkEvent { event ->
          assertThat(event.request).isNotNull()
          assertThat(event.request!!.data).isEqualTo("""{"body":"content"}""")
        },
        anyOrNull(),
      )
  }

  @Test
  fun `attaches request body to SentryEvents on empty ControllerMethod Params`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
    val headers = HttpHeaders().apply { this.contentType = MediaType.APPLICATION_JSON }
    val httpEntity = HttpEntity("""{"body":"content"}""", headers)
    restTemplate.exchange(
      "http://localhost:$port/body",
      HttpMethod.POST,
      httpEntity,
      Void::class.java,
    )

    verify(transport)
      .send(
        checkEvent { event ->
          assertThat(event.request).isNotNull()
          assertThat(event.request!!.data).isEqualTo("""{"body":"content"}""")
        },
        anyOrNull(),
      )
  }

  @Test
  fun `attaches first ip address if multiple addresses exist in a header`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")
    val headers = HttpHeaders()
    headers.put("X-FORWARDED-FOR", listOf("169.128.0.1, 192.168.0.1"))
    val entity = HttpEntity<Void>(headers)

    restTemplate.exchange("http://localhost:$port/hello", HttpMethod.GET, entity, Void::class.java)

    verify(transport)
      .send(
        checkEvent { event ->
          assertThat(event.user).isNotNull()
          assertThat(event.user!!.ipAddress).isEqualTo("169.128.0.1")
        },
        anyOrNull(),
      )
  }

  @Test
  fun `sends events for unhandled exceptions`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

    restTemplate.getForEntity("http://localhost:$port/throws", String::class.java)

    verify(transport)
      .send(
        checkEvent { event ->
          assertThat(event.exceptions).isNotNull().isNotEmpty
          val ex = event.exceptions!!.first()
          assertThat(ex.value).isEqualTo("something went wrong")
          assertThat(ex.mechanism).isNotNull()
          assertThat(ex.mechanism!!.isHandled).isFalse()
        },
        anyOrNull(),
      )
  }

  @Test
  fun `sends events for error logs`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

    restTemplate.getForEntity("http://localhost:$port/logging", String::class.java)

    verify(transport)
      .send(
        checkEvent { event ->
          assertThat(event.message).isNotNull()
          assertThat(event.message!!.message).isEqualTo("event from logger")
        },
        anyOrNull(),
      )
  }

  @Test
  fun `attaches span context to events triggered within transaction`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

    restTemplate.getForEntity("http://localhost:$port/performance", String::class.java)

    verify(transport)
      .send(checkEvent { event -> assertThat(event.contexts.trace).isNotNull() }, anyOrNull())
  }

  @Test
  fun `tracing filter does not overwrite resposne status code`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

    val response =
      restTemplate.getForEntity("http://localhost:$port/performance", String::class.java)
    assertThat(response.statusCode).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
  }

  @Test
  fun `does not send events for handled exceptions`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

    restTemplate.getForEntity("http://localhost:$port/throws-handled", String::class.java)

    verify(scopes, never()).captureEvent(any())
  }

  @Test
  fun `sets user on transaction`() {
    val restTemplate = TestRestTemplate().withBasicAuth("user", "password")

    restTemplate.getForEntity("http://localhost:$port/performance", String::class.java)

    verify(transport)
      .send(
        checkTransaction { transaction ->
          assertThat(transaction.transaction).isEqualTo("GET /performance")
          assertThat(transaction.user).isNotNull()
          assertThat(transaction.user!!.username).isEqualTo("user")
        },
        anyOrNull(),
      )
  }
}

@SpringBootApplication
open class App {
  private val transport = mock<ITransport>().also { whenever(it.isHealthy).thenReturn(true) }

  @Bean
  open fun mockTransportFactory(): ITransportFactory {
    val factory = mock<ITransportFactory>()
    whenever(factory.create(any(), any())).thenReturn(transport)
    return factory
  }

  @Bean open fun mockTransport() = transport

  @Bean
  open fun optionsCallback() =
    Sentry.OptionsConfiguration<SentryOptions> { options ->
      // due to OTel being on the classpath we need to set the default again
      options.spanFactory = DefaultSpanFactory()
      options.openTelemetryMode = SentryOpenTelemetryMode.OFF
    }
}

@RestController
class HelloController(private val helloService: HelloService) {
  private val logger = LoggerFactory.getLogger(HelloController::class.java)

  @GetMapping("/hello")
  fun hello() {
    Sentry.captureMessage("hello")
  }

  @GetMapping("/throws")
  fun throws() {
    throw RuntimeException("something went wrong")
  }

  @GetMapping("/throws-handled")
  fun throwsHandled() {
    throw CustomException("handled exception")
  }

  @GetMapping("/performance")
  fun performance() {
    helloService.throws()
  }

  @GetMapping("/logging")
  fun logging() {
    logger.error("event from logger")
  }

  @PostMapping("/body")
  fun body() {
    Sentry.captureMessage("body")
  }

  @PostMapping("/bodyAsParam")
  fun bodyWithReadingBodyInController(@RequestBody body: String) {
    Sentry.captureMessage("body")
  }
}

@Service
open class HelloService {

  @SentrySpan
  open fun throws() {
    throw RuntimeException("something went wrong")
  }
}

@Configuration
open class SecurityConfiguration {

  @Bean
  open fun userDetailsService(): InMemoryUserDetailsManager {
    val encoder: PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
    val user: UserDetails =
      User.builder()
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
    http
      .csrf { it.disable() }
      .authorizeHttpRequests { it.anyRequest().authenticated() }
      .httpBasic {}

    return http.build()
  }
}

class CustomException(message: String) : RuntimeException(message)

@ControllerAdvice
class ExceptionHandlers {

  @ExceptionHandler(CustomException::class)
  fun handle(e: CustomException) = ResponseEntity.badRequest().build<Void>()
}
