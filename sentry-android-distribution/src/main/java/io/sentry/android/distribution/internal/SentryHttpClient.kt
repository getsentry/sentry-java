package io.sentry.android.distribution.internal

import io.sentry.RequestDetails
import io.sentry.SentryOptions
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

/**
 * HTTP client for making API calls to Sentry's preprod artifacts endpoint.
 *
 * Reuses Sentry's existing networking infrastructure including proxy support, SSL configuration,
 * and timeout settings.
 */
internal class SentryHttpClient(
  private val sentryOptions: SentryOptions,
  private val orgAuthToken: String,
) {

  /** Represents the result of an HTTP request. */
  data class HttpResponse(
    val statusCode: Int,
    val body: String?,
    val isSuccess: Boolean = statusCode in 200..299,
  )

  /**
   * Make a GET request to the specified URL with authorization header.
   *
   * @param url The URL to request
   * @return HttpResponse containing status code and response body
   */
  fun get(url: String): HttpResponse {
    var connection: HttpURLConnection? = null
    try {
      println("SentryHttpClient: Making GET request to: $url")
      connection = createConnection(url)
      
      println("SentryHttpClient: Request headers:")
      connection.requestProperties.forEach { (key, values) ->
        println("  $key: ${values.joinToString(", ")}")
      }
      
      val statusCode = connection.responseCode
      println("SentryHttpClient: Response status: $statusCode")
      
      val body =
        if (statusCode in 200..299) {
          readInputStream(connection.inputStream)
        } else {
          readInputStream(connection.errorStream)
        }
      
      if (body != null) {
        println("SentryHttpClient: Response body length: ${body.length}")
        println("SentryHttpClient: Response body: $body")
      } else {
        println("SentryHttpClient: Response body is null")
      }

      return HttpResponse(statusCode, body)
    } catch (e: IOException) {
      println("SentryHttpClient: IOException occurred: ${e.message}")
      e.printStackTrace()
      return HttpResponse(0, "Network error: ${e.message}")
    } finally {
      connection?.disconnect()
    }
  }

  private fun createConnection(url: String): HttpURLConnection {
    // Create RequestDetails with our custom headers
    val headers =
      mapOf(
        "Authorization" to "Bearer $orgAuthToken",
        "Accept" to "application/json",
        "User-Agent" to "SentryDistribution/1.0",
      )
    val requestDetails = RequestDetails(url, headers)

    // Open connection with proxy support if configured
    val connection =
      if (sentryOptions.proxy != null) {
        val proxy = resolveProxy()
        if (proxy != null) {
          requestDetails.url.openConnection(proxy) as HttpURLConnection
        } else {
          requestDetails.url.openConnection() as HttpURLConnection
        }
      } else {
        requestDetails.url.openConnection() as HttpURLConnection
      }

    // Apply headers
    for ((key, value) in requestDetails.headers) {
      connection.setRequestProperty(key, value)
    }

    // Set request method and timeouts (reusing Sentry's timeout settings)
    connection.requestMethod = "GET"
    connection.connectTimeout = sentryOptions.connectionTimeoutMillis
    connection.readTimeout = sentryOptions.readTimeoutMillis

    // Apply SSL configuration if available
    val sslSocketFactory = sentryOptions.sslSocketFactory
    if (connection is HttpsURLConnection && sslSocketFactory != null) {
      connection.sslSocketFactory = sslSocketFactory
    }

    return connection
  }

  private fun resolveProxy(): java.net.Proxy? {
    val sentryProxy = sentryOptions.proxy ?: return null
    val port = sentryProxy.port
    val host = sentryProxy.host

    if (port != null && host != null) {
      return try {
        val type = sentryProxy.type ?: java.net.Proxy.Type.HTTP
        val proxyAddr = java.net.InetSocketAddress(host, port.toInt())
        java.net.Proxy(type, proxyAddr)
      } catch (e: NumberFormatException) {
        null
      }
    }
    return null
  }

  private fun readInputStream(inputStream: java.io.InputStream?): String? {
    if (inputStream == null) return null

    try {
      inputStream.use { stream ->
        BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader ->
          return reader.readText()
        }
      }
    } catch (e: IOException) {
      return null
    }
  }
}
