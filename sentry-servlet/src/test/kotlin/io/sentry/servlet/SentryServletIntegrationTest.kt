package io.sentry.servlet

import com.google.common.truth.Truth.assertThat
import io.sentry.Sentry
import io.sentry.test.MockSentryServer
import io.sentry.test.event
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.util.ServiceLoader
import javax.servlet.ServletContainerInitializer
import javax.servlet.ServletContext
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import org.apache.catalina.startup.Tomcat
import org.apache.catalina.util.ServerInfo

class SentryServletIntegrationTest {
  private var server: MockSentryServer? = null
  private var servletContext: ServletContext? = null
  private var tomcat: Tomcat? = null

  @BeforeTest
  fun setUp() {
    val mockServer = MockSentryServer()
    server = mockServer
    Sentry.init { it.dsn = mockServer.dsn }

    tomcat =
      Tomcat().apply {
        setBaseDir(Files.createTempDirectory("tomcat-8.5").toString())
        setPort(0)
        connector.setProperty("address", "127.0.0.1")

        val context = addContext("", Files.createTempDirectory("servlet-docbase").toString())
        context.effectiveMajorVersion = 3
        context.effectiveMinorVersion = 1
        context.parentClassLoader = javaClass.classLoader
        Tomcat.addServlet(context, "capture", CaptureServlet())
        context.addServletMappingDecoded("/capture", "capture")
        val initializer =
          ServiceLoader.load(ServletContainerInitializer::class.java)
            .filterIsInstance<SentryServletContainerInitializer>()
            .single()
        context.addServletContainerInitializer(initializer, emptySet())

        start()
        servletContext = context.servletContext
      }
  }

  @AfterTest
  fun tearDown() {
    Sentry.close()
    tomcat?.stop()
    tomcat?.destroy()
    server?.close()
  }

  @Test
  fun `captures request data on Tomcat 8_5 with Servlet 3_1`() {
    assertThat(ServerInfo.getServerNumber()).startsWith("8.5.")
    assertThat(servletContext!!.effectiveMajorVersion).isEqualTo(3)
    assertThat(servletContext!!.effectiveMinorVersion).isEqualTo(1)

    val port = tomcat!!.connector.localPort
    val connection =
      URI("http://127.0.0.1:$port/capture?customer=123").toURL().openConnection()
        as HttpURLConnection

    try {
      assertThat(connection.responseCode).isEqualTo(HttpServletResponse.SC_OK)
      assertThat(connection.inputStream.bufferedReader().use { it.readText().trim() })
        .isEqualTo("captured")
    } finally {
      connection.disconnect()
    }

    val event = server!!.awaitEnvelope { it.event()?.message?.formatted == MESSAGE }.event()!!

    assertThat(event.request?.method).isEqualTo("GET")
    assertThat(event.request?.url).isEqualTo("http://127.0.0.1:$port/capture")
    assertThat(event.request?.queryString).isEqualTo("customer=123")
  }

  private class CaptureServlet : HttpServlet() {
    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
      Sentry.captureMessage(MESSAGE)
      response.writer.print("captured")
    }
  }

  private companion object {
    const val MESSAGE = "captured from servlet integration test"
  }
}
