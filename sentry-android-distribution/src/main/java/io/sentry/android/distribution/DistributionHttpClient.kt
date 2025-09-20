package io.sentry.android.distribution

import io.sentry.SentryLevel
import io.sentry.SentryOptions
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/** HTTP client for making requests to Sentry's distribution API. */
internal class DistributionHttpClient(private val options: SentryOptions) {

  /** Represents the result of an HTTP request. */
  data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val isSuccessful: Boolean = statusCode in 200..299,
  )

  /** Parameters for checking updates. */
  data class UpdateCheckParams(
    val mainBinaryIdentifier: String,
    val appId: String,
    val platform: String = "android",
    val version: String,
  )

  /**
   * Makes a GET request to the distribution API to check for updates.
   *
   * @param params Update check parameters
   * @return HttpResponse containing the response details
   */
  fun checkForUpdates(params: UpdateCheckParams): HttpResponse {
    val distributionOptions = options.distribution
    val orgSlug = distributionOptions.orgSlug
    val projectSlug = distributionOptions.projectSlug
    val authToken = distributionOptions.orgAuthToken
    val baseUrl = distributionOptions.sentryBaseUrl

    if (orgSlug.isEmpty() || projectSlug.isEmpty() || authToken.isEmpty()) {
      throw IllegalStateException(
        "Missing required distribution configuration: orgSlug, projectSlug, or orgAuthToken"
      )
    }

    val queryParams = buildQueryParams(params)
    val url =
      URL(
        "$baseUrl/api/0/projects/$orgSlug/$projectSlug/preprodartifacts/check-for-updates/?$queryParams"
      )

    return try {
      makeRequest(url, authToken)
    } catch (e: IOException) {
      options.logger.log(SentryLevel.ERROR, e, "Network error while checking for updates")
      throw e
    }
  }

  private fun makeRequest(url: URL, authToken: String): HttpResponse {
    val connection = url.openConnection() as HttpURLConnection

    try {
      // Configure connection
      connection.requestMethod = "GET"
      connection.setRequestProperty("Authorization", "Bearer $authToken")
      connection.setRequestProperty("Accept", "application/json")
      connection.setRequestProperty(
        "User-Agent",
        options.sentryClientName ?: "sentry-android-distribution",
      )
      connection.connectTimeout = options.connectionTimeoutMillis
      connection.readTimeout = options.readTimeoutMillis

      // Set SSL socket factory if available
      if (connection is HttpsURLConnection && options.sslSocketFactory != null) {
        connection.sslSocketFactory = options.sslSocketFactory
      }

      // Get response
      val responseCode = connection.responseCode
      val responseBody = readResponse(connection)

      options.logger.log(
        SentryLevel.DEBUG,
        "Distribution API request completed with status: $responseCode",
      )

      return HttpResponse(responseCode, responseBody)
    } finally {
      connection.disconnect()
    }
  }

  private fun readResponse(connection: HttpURLConnection): String {
    val inputStream =
      if (connection.responseCode in 200..299) {
        connection.inputStream
      } else {
        connection.errorStream ?: connection.inputStream
      }

    return inputStream?.use { stream ->
      BufferedReader(InputStreamReader(stream, "UTF-8")).use { reader -> reader.readText() }
    } ?: ""
  }

  private fun buildQueryParams(params: UpdateCheckParams): String {
    val queryParams = mutableListOf<String>()

    queryParams.add(
      "main_binary_identifier=${URLEncoder.encode(params.mainBinaryIdentifier, "UTF-8")}"
    )
    queryParams.add("app_id=${URLEncoder.encode(params.appId, "UTF-8")}")
    queryParams.add("platform=${URLEncoder.encode(params.platform, "UTF-8")}")
    queryParams.add("version=${URLEncoder.encode(params.version, "UTF-8")}")

    return queryParams.joinToString("&")
  }
}
