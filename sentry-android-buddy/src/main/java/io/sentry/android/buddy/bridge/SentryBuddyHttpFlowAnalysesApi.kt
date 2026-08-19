package io.sentry.android.buddy.bridge

import io.sentry.ILogger
import io.sentry.JsonDeserializer
import io.sentry.JsonObjectReader
import io.sentry.JsonSerializer
import io.sentry.NoOpLogger
import io.sentry.ObjectReader
import io.sentry.SentryOptions
import io.sentry.android.buddy.model.AnalysisStatus
import io.sentry.android.buddy.model.FlowAnalysisRequest
import io.sentry.android.buddy.model.FlowAnalysisResponse
import io.sentry.android.buddy.model.Recommendation
import io.sentry.android.buddy.model.RecommendationStatus
import io.sentry.android.buddy.model.SentryIssue
import io.sentry.android.buddy.model.Severity
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public class SentryBuddyHttpFlowAnalysesApi
@JvmOverloads
public constructor(
  private val baseUrl: String,
  private val client: OkHttpClient = OkHttpClient(),
) : SentryBuddyFlowAnalysesApi {
  private val json = JsonSerializer(SentryOptions())
  private val logger = NoOpLogger.getInstance()

  override fun submit(request: FlowAnalysisRequest): FlowAnalysisResponse {
    val httpRequest =
      Request.Builder()
        .url(flowAnalysisUrl())
        .post(serialize(request).toRequestBody(JSON_MEDIA_TYPE))
        .build()
    return execute(httpRequest, FlowAnalysisResponseDeserializer)
  }

  override fun get(flowId: String): FlowAnalysisResponse {
    val httpRequest = Request.Builder().url(flowAnalysisUrl(flowId)).get().build()
    return execute(httpRequest, FlowAnalysisResponseDeserializer)
  }

  override fun resolveRecommendation(flowId: String, recommendationId: String): Recommendation {
    val httpRequest =
      Request.Builder()
        .url(flowAnalysisUrl(flowId, "recommendations", recommendationId, "resolve"))
        .post(ByteArray(0).toRequestBody(null))
        .build()
    return execute(httpRequest, RecommendationDeserializer)
  }

  private fun flowAnalysisUrl(vararg pathSegments: String): okhttp3.HttpUrl {
    val builder = baseUrl.toHttpUrl().newBuilder().addPathSegments("v1/flow-analysis")
    pathSegments.forEach { builder.addPathSegment(it) }
    return builder.build()
  }

  private fun serialize(request: FlowAnalysisRequest): String {
    val writer = StringWriter()
    json.serialize(request, writer)
    return writer.toString()
  }

  private fun <T> execute(request: Request, deserializer: JsonDeserializer<T>): T {
    try {
      client.newCall(request).execute().use { response ->
        val body = response.body?.string().orEmpty()
        if (!response.isSuccessful) {
          throw IllegalStateException(response.errorMessage(body))
        }
        if (body.isBlank()) {
          throw IllegalStateException("Flow analysis bridge returned an empty response.")
        }
        return parse(body, deserializer)
      }
    } catch (exception: IOException) {
      throw IllegalStateException(
        "Failed to call flow analysis bridge: ${exception.message}",
        exception,
      )
    }
  }

  private fun Response.errorMessage(body: String): String {
    val error = extractError(body)
    return buildString {
      append("Flow analysis bridge request failed with HTTP ").append(code)
      error?.let { append(": ").append(it) }
    }
  }

  private fun <T> parse(body: String, deserializer: JsonDeserializer<T>): T {
    try {
      return JsonObjectReader(StringReader(body)).use { deserializer.deserialize(it, logger) }
    } catch (exception: Exception) {
      throw IllegalStateException("Failed to parse flow analysis bridge response.", exception)
    }
  }

  private fun extractError(body: String): String? {
    if (body.isBlank()) {
      return null
    }
    return try {
      (JsonObjectReader(StringReader(body)).use { it.nextObjectOrNull() } as? Map<*, *>)
        ?.get("error")
        ?.toString()
    } catch (_: Exception) {
      body.take(200)
    }
  }

  private companion object {
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }
}

internal object FlowAnalysisResponseDeserializer : JsonDeserializer<FlowAnalysisResponse> {
  override fun deserialize(reader: ObjectReader, logger: ILogger): FlowAnalysisResponse {
    var flowId: String? = null
    var status: AnalysisStatus? = null
    var title: String? = null
    var recommendations: List<Recommendation> = emptyList()
    var issues: List<SentryIssue> = emptyList()
    var error: String? = null
    var enrichmentErrors: List<String> = emptyList()

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "flow_id" -> flowId = reader.nextStringOrNull()
        "status" -> status = reader.nextStringOrNull()?.let { AnalysisStatus.valueOf(it) }
        "title" -> title = reader.nextStringOrNull()
        "recommendations" ->
          recommendations = reader.nextListOrNull(logger, RecommendationDeserializer).orEmpty()

        "issues" -> issues = reader.nextListOrNull(logger, SentryIssueDeserializer).orEmpty()
        "error" -> error = reader.nextStringOrNull()
        "enrichment_errors" ->
          enrichmentErrors =
            (reader.nextObjectOrNull() as? List<*>).orEmpty().filterIsInstance<String>()

        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return FlowAnalysisResponse(
      flowId = requireNotNull(flowId) { "flow_id is required" },
      status = requireNotNull(status) { "status is required" },
      title = title,
      recommendations = recommendations,
      issues = issues,
      error = error,
      enrichmentErrors = enrichmentErrors,
    )
  }
}

internal object RecommendationDeserializer : JsonDeserializer<Recommendation> {
  override fun deserialize(reader: ObjectReader, logger: ILogger): Recommendation {
    var id: String? = null
    var title: String? = null
    var description: String? = null
    var link: String? = null
    var severity: Severity = Severity.MEDIUM
    var resolvable = true
    var status: RecommendationStatus = RecommendationStatus.OPEN
    var seerRunUrl: String? = null

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "id" -> id = reader.nextStringOrNull()
        "title" -> title = reader.nextStringOrNull()
        "description" -> description = reader.nextStringOrNull()
        "link" -> link = reader.nextStringOrNull()
        "severity" -> severity = reader.nextStringOrNull()?.let { Severity.valueOf(it) } ?: severity
        "resolvable" -> resolvable = reader.nextBooleanOrNull() ?: resolvable
        "status" ->
          status = reader.nextStringOrNull()?.let { RecommendationStatus.valueOf(it) } ?: status

        "seer_run_url" -> seerRunUrl = reader.nextStringOrNull()
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return Recommendation(
      id = requireNotNull(id) { "recommendation id is required" },
      title = requireNotNull(title) { "recommendation title is required" },
      description = requireNotNull(description) { "recommendation description is required" },
      link = link,
      severity = severity,
      resolvable = resolvable,
      status = status,
      seerRunUrl = seerRunUrl,
    )
  }
}

internal object SentryIssueDeserializer : JsonDeserializer<SentryIssue> {
  override fun deserialize(reader: ObjectReader, logger: ILogger): SentryIssue {
    var id: String? = null
    var title: String? = null
    var culprit: String? = null
    var count: Int? = null
    var level: String? = null
    var permalink: String? = null

    reader.beginObject()
    while (reader.hasNext()) {
      when (reader.nextName()) {
        "id" -> id = reader.nextStringOrNull()
        "title" -> title = reader.nextStringOrNull()
        "culprit" -> culprit = reader.nextStringOrNull()
        "count" -> count = reader.nextIntegerOrNull()
        "level" -> level = reader.nextStringOrNull()
        "permalink" -> permalink = reader.nextStringOrNull()
        else -> reader.skipValue()
      }
    }
    reader.endObject()

    return SentryIssue(
      id = requireNotNull(id) { "issue id is required" },
      title = requireNotNull(title) { "issue title is required" },
      culprit = culprit,
      count = requireNotNull(count) { "issue count is required" },
      level = requireNotNull(level) { "issue level is required" },
      permalink = requireNotNull(permalink) { "issue permalink is required" },
    )
  }
}
