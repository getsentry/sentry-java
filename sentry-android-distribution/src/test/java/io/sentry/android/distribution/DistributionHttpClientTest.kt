package io.sentry.android.distribution

import io.sentry.SentryOptions
import org.junit.Assert.*
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class DistributionHttpClientTest {

  private lateinit var options: SentryOptions
  private lateinit var httpClient: DistributionHttpClient

  @Before
  fun setUp() {
    options =
      SentryOptions().apply {
        connectionTimeoutMillis = 10000
        readTimeoutMillis = 10000
      }

    options.distribution.apply {
      orgSlug = "sentry"
      projectSlug = "launchpad-test"
      orgAuthToken = "DONT_CHECK_THIS_IN"
      sentryBaseUrl = "https://sentry.io"
    }

    httpClient = DistributionHttpClient(options)
  }

  @Test
  @Ignore("This is just used for testing against the real API.")
  fun `test checkForUpdates with real API`() {
    val params =
      DistributionHttpClient.UpdateCheckParams(
        mainBinaryIdentifier = "com.emergetools.hackernews",
        appId = "com.emergetools.hackernews",
        platform = "android",
        version = "1.0.0",
      )

    val response = httpClient.checkForUpdates(params)

    // Print response for debugging
    println("HTTP Status: ${response.statusCode}")
    println("Response Body: ${response.body}")
    println("Is Successful: ${response.isSuccessful}")

    // Basic assertions
    assertTrue("Response should have a status code", response.statusCode > 0)
    assertNotNull("Response body should not be null", response.body)
  }
}
