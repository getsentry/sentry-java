package io.sentry.apollo3

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.exception.ApolloException
import io.sentry.Hint
import io.sentry.IHub
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.SentryOptions.DEFAULT_PROPAGATION_TARGETS
import io.sentry.TypeCheckHint
import io.sentry.apollo3.SentryApollo3HttpInterceptor.Companion.DEFAULT_CAPTURE_FAILED_REQUESTS
import io.sentry.exception.ExceptionMechanismException
import io.sentry.protocol.SdkVersion
import io.sentry.protocol.SentryId
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SentryApollo3InterceptorClientErrors {
    class Fixture {
        val server = MockWebServer()
        lateinit var hub: IHub

        private val responseBodyOk =
            """{
  "data": {
    "launch": {
      "__typename": "Launch",
      "id": "83",
      "site": "CCAFS SLC 40",
      "mission": {
        "__typename": "Mission",
        "name": "Amos-17",
        "missionPatch": "https://images2.imgbox.com/a0/ab/XUoByiuR_o.png"
      }
    }
  }
}"""

        val responseBodyNotOk =
            """{
  "errors": [
    {
      "message": "Cannot query field \"mySite\" on type \"Launch\". Did you mean \"site\"?",
      "extensions": {
        "code": "GRAPHQL_VALIDATION_FAILED"
      }
    }
  ]
}"""

        fun getSut(
            captureFailedRequests: Boolean = DEFAULT_CAPTURE_FAILED_REQUESTS,
            failedRequestTargets: List<String> = listOf(DEFAULT_PROPAGATION_TARGETS),
            httpStatusCode: Int = 200,
            responseBody: String = responseBodyOk,
            sendDefaultPii: Boolean = false,
            socketPolicy: SocketPolicy = SocketPolicy.KEEP_OPEN
        ): ApolloClient {
            SentryIntegrationPackageStorage.getInstance().clearStorage()

            hub = mock<IHub>().apply {
                whenever(options).thenReturn(
                    SentryOptions().apply {
                        dsn = "https://key@sentry.io/proj"
                        sdkVersion = SdkVersion("test", "1.2.3")
                        isSendDefaultPii = sendDefaultPii
                    }
                )
            }
            whenever(hub.captureEvent(any(), any<Hint>())).thenReturn(SentryId.EMPTY_ID)

            val response = MockResponse()
                .setBody(responseBody)
                .setSocketPolicy(socketPolicy)
                .setResponseCode(httpStatusCode)

            if (sendDefaultPii) {
                response.addHeader("Set-Cookie", "Test")
            }

            server.enqueue(
                response
            )

            val builder = ApolloClient.Builder()
                .serverUrl(server.url("?myQuery=query#myFragment").toString())
                .sentryTracing(
                    hub = hub,
                    captureFailedRequests = captureFailedRequests,
                    failedRequestTargets = failedRequestTargets
                )
            if (sendDefaultPii) {
                builder.addHttpHeader("Cookie", "Test")
            }

            return builder.build()
        }
    }

    private val fixture = Fixture()

    // region captureFailedRequests

    @Test
    fun `does not capture errors if captureFailedRequests is disabled`() {
        val sut = fixture.getSut(captureFailedRequests = false, responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)

        verify(fixture.hub, never()).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `capture errors if captureFailedRequests is enabled`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(any(), any<Hint>())
    }

    // endregion

    // region Apollo3ClientError

    @Test
    fun `does not add Apollo3ClientError integration if captureFailedRequests is disabled`() {
        fixture.getSut(captureFailedRequests = false)

        assertFalse(SentryIntegrationPackageStorage.getInstance().integrations.contains("Apollo3ClientError"))
    }

    @Test
    fun `adds Apollo3ClientError integration if captureFailedRequests is enabled`() {
        fixture.getSut()

        assertTrue(SentryIntegrationPackageStorage.getInstance().integrations.contains("Apollo3ClientError"))
    }

    // endregion

    // region failedRequestTargets

    @Test
    fun `does not capture errors if failedRequestTargets does not match`() {
        val sut = fixture.getSut(
            failedRequestTargets = listOf("nope.com"),
            responseBody = fixture.responseBodyNotOk
        )
        executeQuery(sut)

        verify(fixture.hub, never()).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `capture errors if failedRequestTargets matches`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(any(), any<Hint>())
    }

    // endregion

    // region SentryEvent

    @Test
    fun `capture errors with SentryApollo3Interceptor mechanism`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(
            check {
                val throwable = (it.throwableMechanism as ExceptionMechanismException)
                assertEquals("SentryApollo3Interceptor", throwable.exceptionMechanism.type)
            },
            any<Hint>()
        )
    }

    @Test
    fun `capture errors with title`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(
            check {
                val throwable = (it.throwableMechanism as ExceptionMechanismException)
                assertEquals("GraphQL Request failed, name: LaunchDetails, type: query", throwable.throwable.message)
            },
            any<Hint>()
        )
    }

    @Test
    fun `capture errors with snapshot flag set`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(
            check {
                val throwable = (it.throwableMechanism as ExceptionMechanismException)
                assertTrue(throwable.isSnapshot)
            },
            any<Hint>()
        )
    }

    private val escapeDolar = "\$id"

    @Test
    fun `capture errors with request context`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)
        val body =
            """
{"operationName":"LaunchDetails","variables":{"id":"83"},"query":"query LaunchDetails($escapeDolar: ID!) { launch(id: $escapeDolar) { id site mission { name missionPatch(size: LARGE) } rocket { name type } } }"}
            """.trimIndent()

        verify(fixture.hub).captureEvent(
            check {
                val request = it.request!!

                assertEquals("http://localhost:${fixture.server.port}/", request.url)
                assertEquals("myQuery=query", request.queryString)
                assertEquals("myFragment", request.fragment)
                assertEquals("Post", request.method)
                assertEquals("graphql", request.apiTarget)
                assertEquals(193L, request.bodySize)
                assertEquals(body, request.data)
                assertNull(request.cookies)
                assertNull(request.headers)
            },
            any<Hint>()
        )
    }

    @Test
    fun `capture errors with more request context if sendDefaultPii is enabled`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk, sendDefaultPii = true)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(
            check {
                val request = it.request!!

                assertEquals("Test", request.cookies)
                assertNotNull(request.headers)
                assertEquals("LaunchDetails", request.headers?.get("X-APOLLO-OPERATION-NAME"))
            },
            any<Hint>()
        )
    }

    @Test
    fun `capture errors with response context`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(
            check {
                val response = it.contexts.response!!

                assertEquals(200, response.statusCode)
                assertEquals(200, response.bodySize)
                assertEquals(fixture.responseBodyNotOk, response.data)
                assertNull(response.cookies)
                assertNull(response.headers)
            },
            any<Hint>()
        )
    }

    @Test
    fun `capture errors with more response context if sendDefaultPii is enabled`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk, sendDefaultPii = true)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(
            check {
                val response = it.contexts.response!!

                assertEquals("Test", response.cookies)
                assertNotNull(response.headers)
                assertEquals(200, response.headers?.get("Content-Length")?.toInt())
            },
            any<Hint>()
        )
    }

    @Test
    fun `capture errors with specific fingerprints`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(
            check {
                assertEquals(listOf("LaunchDetails", "query", "200"), it.fingerprints)
            },
            any<Hint>()
        )
    }

    // endregion

    // region errors

    @Test
    fun `capture errors if response code is equal or higher than 400`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk, httpStatusCode = 500)
        executeQuery(sut)

        // HttpInterceptor does not throw for >= 400
        verify(fixture.hub).captureEvent(any(), any<Hint>())
    }

    @Test
    fun `capture errors swallow any exception during the error transformation`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)

        whenever(fixture.hub.captureEvent(any(), any<Hint>())).thenThrow(RuntimeException())

        executeQuery(sut)
    }

    // endregion

    // region hints

    @Test
    fun `hints are set when capturing errors`() {
        val sut =
            fixture.getSut(responseBody = fixture.responseBodyNotOk)
        executeQuery(sut)

        verify(fixture.hub).captureEvent(
            any(),
            check<Hint> {
                val request = it.get(TypeCheckHint.APOLLO_REQUEST)
                assertNotNull(request)
                assertTrue(request is HttpRequest)

                val response = it.get(TypeCheckHint.APOLLO_RESPONSE)
                assertNotNull(response)
                assertTrue(response is HttpResponse)
            }
        )
    }

    // endregion

    private fun executeQuery(sut: ApolloClient, id: String = "83") = runBlocking {
        val coroutine = launch {
            try {
                sut.query(LaunchDetailsQuery(id)).execute()
            } catch (e: ApolloException) {
                return@launch
            }
        }

        coroutine.join()
    }
}
