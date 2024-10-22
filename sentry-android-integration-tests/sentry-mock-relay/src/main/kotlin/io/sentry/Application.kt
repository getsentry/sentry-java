package io.sentry

import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.request.uri
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.zip.GZIPInputStream

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureRouting()
}

fun Application.configureRouting() {
    val receivedEnvelopes = mutableListOf<List<JsonElement>>()

    routing {
        post("/{...}") {
            println("Received request: ${call.request.uri}")
            val textBody: String = if (call.request.headers["Content-Encoding"] == "gzip") {
                call.receive<ByteArray>().let {
                    GZIPInputStream(it.inputStream()).bufferedReader().use { reader ->
                        reader.readText()
                    }
                }
            } else {
                call.receiveText()
            }

            val jsonItems = textBody.split('\n').mapNotNull { line ->
                try {
                    Json.parseToJsonElement(line)
                } catch (e: Exception) {
                    null
                }
            }

            receivedEnvelopes.add(jsonItems)

            call.respondText("{}")
        }
        get("/healthCheck") {
            call.respondText("OK")
        }
        get("/assertReceivedAtLeastOneCrashReport") {
            if (receivedEnvelopes.isEmpty()) {
                call.respondText("Mocked Replay have not received any envelopes", status = io.ktor.http.HttpStatusCode.BadRequest)
            }

            val hasCrashReport = receivedEnvelopes.any { envelope ->
                envelope.any { item ->
                    try {
                        if (item.jsonObject.containsKey("exception")) {
                            val exception = item.jsonObject["exception"]?.jsonObject
                            val values = exception?.get("values")?.jsonArray
                            values?.any { value ->
                                val message = value.jsonObject["value"]?.jsonPrimitive?.content
                                message == "Crash the test app."
                            } ?: false
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
            }

            if (hasCrashReport) {
                call.respondText("Received at least one crash report")
            } else {
                call.respondText("No crash report received", status = io.ktor.http.HttpStatusCode.BadRequest)
            }
        }
    }
}
