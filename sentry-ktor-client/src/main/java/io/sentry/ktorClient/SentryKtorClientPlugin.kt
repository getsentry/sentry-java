package io.sentry.ktorClient

import io.ktor.client.HttpClient
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.api.ClientPlugin
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.sentry.BaggageHeader
import io.sentry.BuildConfig
import io.sentry.HttpStatusCodeRange
import io.sentry.IScopes
import io.sentry.ISpan
import io.sentry.ScopesAdapter
import io.sentry.Sentry
import io.sentry.SentryDate
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryOptions
import io.sentry.SpanStatus
import io.sentry.kotlin.SentryContext
import io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion
import io.sentry.util.Platform
import io.sentry.util.PropagationTargetsUtils
import io.sentry.util.SpanUtils
import io.sentry.util.TracingUtils
import kotlinx.coroutines.withContext

/** Configuration for the Sentry Ktor client plugin. */
public class SentryKtorClientPluginConfig {
  /** The [IScopes] instance to use. Defaults to [ScopesAdapter.getInstance]. */
  public var scopes: IScopes = ScopesAdapter.getInstance()

  /** Callback to customize or drop spans before they are created. Return null to drop the span. */
  public var beforeSpan: BeforeSpanCallback? = null

  /** Whether to capture HTTP client errors as Sentry events. Defaults to true. */
  public var captureFailedRequests: Boolean = true

  /**
   * The HTTP status code ranges that should be considered as failed requests. Defaults to 500-599
   * (server errors).
   */
  public var failedRequestStatusCodes: List<HttpStatusCodeRange> =
    listOf(HttpStatusCodeRange(HttpStatusCodeRange.DEFAULT_MIN, HttpStatusCodeRange.DEFAULT_MAX))

  /**
   * The list of targets (URLs) for which failed requests should be captured. Supports regex
   * patterns. Defaults to capture all requests.
   */
  public var failedRequestTargets: List<String> = listOf(SentryOptions.DEFAULT_PROPAGATION_TARGETS)

  /** Callback interface for customizing spans before they are created. */
  public fun interface BeforeSpanCallback {
    /**
     * Customize or drop a span before it's created.
     *
     * @param span The span to customize
     * @param request The HTTP request being executed
     * @return The customized span, or null to drop the span
     */
    public fun execute(span: ISpan, request: HttpRequest): ISpan?
  }

  /**
   * Forcefully use the passed in scope instead of relying on the one injected by [SentryContext].
   * Used for testing.
   */
  internal var forceScopes: Boolean = false
}

internal const val SENTRY_KTOR_CLIENT_PLUGIN_KEY = "SentryKtorClientPlugin"
internal const val TRACE_ORIGIN = "auto.http.ktor-client"

/**
 * Sentry plugin for Ktor HTTP client that provides automatic instrumentation for HTTP requests,
 * including error capturing, request/response breadcrumbs, and distributed tracing.
 */
public val SentryKtorClientPlugin: ClientPlugin<SentryKtorClientPluginConfig> =
  createClientPlugin(SENTRY_KTOR_CLIENT_PLUGIN_KEY, ::SentryKtorClientPluginConfig) {
    // Init
    SentryIntegrationPackageStorage.getInstance()
      .addPackage("maven:io.sentry:sentry-ktor-client", BuildConfig.VERSION_NAME)
    addIntegrationToSdkVersion("Ktor")

    // Options
    val scopes = pluginConfig.scopes
    val beforeSpan = pluginConfig.beforeSpan
    val captureFailedRequests = pluginConfig.captureFailedRequests
    val failedRequestStatusCodes = pluginConfig.failedRequestStatusCodes
    val failedRequestTargets = pluginConfig.failedRequestTargets
    val forceScopes = pluginConfig.forceScopes

    // Attributes
    // Request start time for breadcrumbs
    val requestStartTimestampKey = AttributeKey<SentryDate>("SentryRequestStartTimestamp")
    // Span associated with the request
    val requestSpanKey = AttributeKey<ISpan>("SentryRequestSpan")

    onRequest { request, _ ->
      request.attributes.put(
        requestStartTimestampKey,
        Sentry.getCurrentScopes().options.dateProvider.now(),
      )

      val parentSpan: ISpan? =
        if (forceScopes) scopes.getSpan()
        else {
          if (Platform.isAndroid()) scopes.transaction else scopes.span
        }

      val spanOp = "http.client"
      val spanDescription = "${request.method.value.toString()} ${request.url.buildString()}"
      val span: ISpan? = parentSpan?.startChild(spanOp, spanDescription)
      if (span != null) {
        span.spanContext.origin = TRACE_ORIGIN
        request.attributes.put(requestSpanKey, span)
      }

      if (
        !SpanUtils.isIgnored(
          (if (forceScopes) scopes else Sentry.getCurrentScopes()).options.getIgnoredSpanOrigins(),
          TRACE_ORIGIN,
        )
      ) {
        TracingUtils.traceIfAllowed(
            if (forceScopes) scopes else Sentry.getCurrentScopes(),
            request.url.buildString(),
            request.headers.getAll(BaggageHeader.BAGGAGE_HEADER),
            span,
          )
          ?.let { tracingHeaders ->
            request.headers[tracingHeaders.sentryTraceHeader.name] =
              tracingHeaders.sentryTraceHeader.value
            tracingHeaders.baggageHeader?.let {
              request.headers.remove(BaggageHeader.BAGGAGE_HEADER)
              request.headers[it.name] = it.value
            }
          }
      }
    }

    client.requestPipeline.intercept(HttpRequestPipeline.Before) {
      try {
        proceed()
      } catch (t: Throwable) {
        context.attributes.getOrNull(requestSpanKey)?.apply {
          throwable = t
          status = SpanStatus.INTERNAL_ERROR
          finish()
        }
        throw t
      }
    }

    onResponse { response ->
      val request = response.request
      val startTimestamp = response.call.attributes.getOrNull(requestStartTimestampKey)
      val endTimestamp = Sentry.getCurrentScopes().options.dateProvider.now()

      if (
        captureFailedRequests &&
          failedRequestStatusCodes.any { it.isInRange(response.status.value) } &&
          PropagationTargetsUtils.contain(failedRequestTargets, request.url.toString())
      ) {
        SentryKtorClientUtils.captureClientError(scopes, request, response)
      }

      SentryKtorClientUtils.addBreadcrumb(scopes, request, response, startTimestamp, endTimestamp)

      response.call.attributes.getOrNull(requestSpanKey)?.let { span ->
        var result: ISpan? = span

        if (beforeSpan != null) {
          result = beforeSpan.execute(span, request)
        }

        if (result == null) {
          // span is dropped
          span.spanContext.sampled = false
        }

        val spanStatus = SpanStatus.fromHttpStatusCode(response.status.value)
        span.finish(spanStatus, endTimestamp)
      }
    }

    on(SentryKtorClientPluginContextHook(scopes)) { block -> block() }
  }

public open class SentryKtorClientPluginContextHook(protected val scopes: IScopes) :
  ClientHook<suspend (suspend () -> Unit) -> Unit> {
  private val phase = PipelinePhase("SentryKtorClientPluginContext")

  override fun install(client: HttpClient, handler: suspend (suspend () -> Unit) -> Unit) {
    client.requestPipeline.insertPhaseBefore(HttpRequestPipeline.Before, phase)
    client.requestPipeline.intercept(phase) {
      val scopes =
        this@SentryKtorClientPluginContextHook.scopes.forkedCurrentScope(
          SENTRY_KTOR_CLIENT_PLUGIN_KEY
        )
      withContext(SentryContext(scopes)) { proceed() }
    }
  }
}
