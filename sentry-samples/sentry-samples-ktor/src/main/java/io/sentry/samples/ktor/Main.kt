package io.sentry.samples.ktor

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.sentry.Sentry
import io.sentry.TransactionOptions
import io.sentry.ktor.SentryKtorClientPlugin
import kotlinx.coroutines.runBlocking

fun main() {
  Sentry.init { options ->
    options.dsn =
      "https://b9ca97be3ff8f1cef41dffdcb1e5100b@o447951.ingest.us.sentry.io/4508683222843393"
    options.isDebug = true
    options.isSendDefaultPii = true
    options.tracesSampleRate = 1.0
    options.addInAppInclude("io.sentry.samples")
  }

  val client =
    HttpClient(Java) { install(SentryKtorClientPlugin) { failedRequestTargets = listOf(".*") } }

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

  // Should create errors
  client.get("https://httpbin.org/status/500")
  client.get("https://httpbin.org/status/500?lol=test")

  // Should create breadcrumb
  client.post("https://httpbin.org/post") {
    setBody("{ \"message\": \"Hello from Sentry Ktor Client!\" }")
    headers {
      append("Content-Type", "application/json")
      append("X-Custom-Header", "Sentry-Ktor-Sample")
    }
  }
}
