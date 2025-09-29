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
    val versionCode: Long,
    val versionName: String,
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

    if (orgSlug.isNullOrEmpty() || projectSlug.isNullOrEmpty() || authToken.isNullOrEmpty()) {
      throw IllegalStateException(
        "Missing required distribution configuration: orgSlug, projectSlug, or orgAuthToken"
      )
    }

    val urlString = buildString {
      append(baseUrl.trimEnd('/'))
      append(
        "/api/0/projects/${URLEncoder.encode(orgSlug, "UTF-8")}/${URLEncoder.encode(projectSlug, "UTF-8")}/preprodartifacts/check-for-updates/"
      )
      append("?main_binary_identifier=${URLEncoder.encode(params.mainBinaryIdentifier, "UTF-8")}")
      append("&app_id=${URLEncoder.encode(params.appId, "UTF-8")}")
      append("&platform=${URLEncoder.encode(params.platform, "UTF-8")}")
      append("&build_number=${URLEncoder.encode(params.versionCode.toString(), "UTF-8")}")
      append("&build_version=${URLEncoder.encode(params.versionName, "UTF-8")}")
    }
    val url = URL(urlString)

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
      connection.requestMethod = "GET"
      connection.setRequestProperty("Authorization", "Bearer $authToken")
      connection.setRequestProperty("Accept", "application/json")
      connection.setRequestProperty(
        "User-Agent",
        options.sentryClientName ?: throw IllegalStateException("sentryClientName must be set"),
      )
      connection.connectTimeout = options.connectionTimeoutMillis
      connection.readTimeout = options.readTimeoutMillis

      if (connection is HttpsURLConnection && options.sslSocketFactory != null) {
        connection.sslSocketFactory = options.sslSocketFactory
      }

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
}
