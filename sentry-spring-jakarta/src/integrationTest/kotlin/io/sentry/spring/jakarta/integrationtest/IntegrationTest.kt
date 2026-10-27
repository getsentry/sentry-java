package io.sentry.spring.jakarta.integrationtest

import io.sentry.test.MockSentryServer
import jakarta.servlet.ServletContainerInitializer
import java.nio.file.Files
import java.util.ServiceLoader
import org.apache.catalina.startup.Tomcat
import org.junit.Before
import org.springframework.http.client.ClientHttpResponse
import org.springframework.web.client.DefaultResponseErrorHandler
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.DefaultUriBuilderFactory

/**
 * Drives [AppInitializer]'s app over HTTP and asserts what reaches [server].
 *
 * Subclasses share one app and one server. Envelopes that arrive late can outlive [server]'s reset,
 * so tests match on values only they produce.
 */
abstract class IntegrationTest {
  val server = TomcatApp.server
  val restTemplate = TomcatApp.restTemplate

  @Before
  fun resetServer() {
    server.reset()
  }
}

/**
 * The app in an embedded Tomcat, with no Spring Boot on the classpath, so Sentry takes the same
 * path as in a WAR. Tomcat starts once per test JVM.
 */
object TomcatApp {
  val server = MockSentryServer()

  init {
    System.setProperty("sentry.dsn", server.dsn)
    // The profiler rejects a traces path with a dot anywhere in it, which the module's absolute
    // path can have.
    System.setProperty(
      "sentry.profiling-traces-dir-path",
      "${System.getProperty("java.io.tmpdir")}/sentry-integration-test/profiling-traces",
    )
  }

  private val tomcat =
    Tomcat().apply {
      setBaseDir(Files.createTempDirectory("tomcat").toString())
      setPort(0)
      // Creates the default connector.
      connector
      val context = addContext("", Files.createTempDirectory("docbase").toString())
      // As Tomcat does for a WAR, runs each initializer the classpath registers: Spring's starts
      // AppInitializer's app, and Sentry's adds SentrySpringFilter. Only Spring's uses the set.
      ServiceLoader.load(ServletContainerInitializer::class.java).forEach {
        context.addServletContainerInitializer(it, setOf(AppInitializer::class.java))
      }
      start()
    }

  val restTemplate =
    RestTemplate().apply {
      uriTemplateHandler =
        DefaultUriBuilderFactory("http://localhost:${tomcat.connector.localPort}")
      // Returns error responses rather than throwing them, so tests can check a failed request.
      errorHandler =
        object : DefaultResponseErrorHandler() {
          override fun hasError(response: ClientHttpResponse): Boolean = false
        }
    }
}
