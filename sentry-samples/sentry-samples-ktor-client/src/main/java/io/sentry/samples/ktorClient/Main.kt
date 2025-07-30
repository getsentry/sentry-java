package io.sentry.samples.ktorClient

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.sentry.HttpStatusCodeRange
import io.sentry.Sentry
import io.sentry.TransactionOptions
import io.sentry.ktorClient.SentryKtorClientPlugin
import kotlinx.coroutines.runBlocking

fun main() {
  Sentry.init { options ->
    options.dsn = "https://502f25099c204a2fbf4cb16edc5975d1@o447951.ingest.sentry.io/5428563"
    options.isDebug = true
    options.isSendDefaultPii = true
    options.tracesSampleRate = 1.0
    options.addInAppInclude("io.sentry.samples")
    options.isGlobalHubMode = true
  }

  val client =
    HttpClient(Java) {
      install(SentryKtorClientPlugin) {
        captureFailedRequests = true
        failedRequestTargets = listOf(".*")
        failedRequestStatusCodes = listOf(HttpStatusCodeRange(500, 599))
      }
    }

  val opts = TransactionOptions().apply { isBindToScope = true }
  val tx = Sentry.startTransaction("My Transaction", "test", opts)

  runBlocking { makeRequests(client) }

  Sentry.captureMessage("Ktor client sample done")
  tx.finish()
}

suspend fun makeRequests(client: HttpClient) {
  // Should create breadcrumbs
  client.get("https://httpbin.org/get")
  client.get("https://httpbin.org/status/404")

  // Should create breadcrumbs and errors
  client.get("https://httpbin.org/status/500?lol=test") // no tags
  Sentry.setTag("lol", "test")
  client.get("https://httpbin.org/status/502") // lol: test
  Sentry.removeTag("lol")
  client.get("https://httpbin.org/status/503?lol=test") // no tags

  // Should create breadcrumb
  client.post("https://httpbin.org/post") {
    setBody("{ \"message\": \"Hello from Sentry Ktor Client!\" }")
    headers {
      append("Content-Type", "application/json")
      append("X-Custom-Header", "Sentry-Ktor-Sample")
    }
  }
}
