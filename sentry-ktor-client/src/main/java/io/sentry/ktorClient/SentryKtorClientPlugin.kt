package io.sentry.ktorClient

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttpConfig
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
import java.lang.reflect.Field
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

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

  /** Whether the plugin is enabled. If disabled, the plugin has no effect. Defaults to true. */
  public var enabled: Boolean = true

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
    /**
     * Disables the plugin, if necessary.
     *
     * Currently, the only case in which we want to disable the plugin is when we detect that the
     * OkHttp engine is used and SentryOkHttpInterceptor is registered, as otherwise all HTTP
     * requests would be doubly instrumented.
     */
    fun maybeDisable() {
      if (client.engine.config is OkHttpConfig) {
        val config = client.engine.config as OkHttpConfig

        // Case 1: OkHttp client initialized by Ktor and configured with a `config` block.
        //
        // The OkHttp client is initialized only upon the first request.
        // Attempt to initialize a client to inspect the interceptors that are registered on it.
        try {
          val configField: Field =
            OkHttpConfig::class.java.getDeclaredField("config").apply { isAccessible = true }
          val configFunction = configField.get(config) as? (OkHttpClient.Builder.() -> Unit)

          if (configFunction != null) {
            val builder = OkHttpClient.Builder()
            configFunction.invoke(builder)
            val client = builder.build()
            if (client.interceptors.any { it.javaClass.name.contains("SentryOkHttpInterceptor") }) {
              pluginConfig.enabled = false
            }
          }
        } catch (_: Throwable) {}

        // Case 2: pre-configured OkHttp client passed in.
        val client = config.preconfigured
        if (client != null) {
          if (client.interceptors.any { it.javaClass.name.contains("SentryOkHttpInterceptor") }) {
            pluginConfig.enabled = false
          }
        }
      }
    }

    maybeDisable()

    if (!pluginConfig.enabled) {
      return@createClientPlugin
    }

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
      if (!this@createClientPlugin.pluginConfig.enabled) {
        return@onRequest
      }

      request.attributes.put(
        requestStartTimestampKey,
        (if (forceScopes) scopes else Sentry.getCurrentScopes()).options.dateProvider.now(),
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
      if (!this@createClientPlugin.pluginConfig.enabled) {
        proceed()
      }

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
      if (!this@createClientPlugin.pluginConfig.enabled) {
        return@onResponse
      }

      val request = response.request
      val startTimestamp = response.call.attributes.getOrNull(requestStartTimestampKey)
      val endTimestamp =
        (if (forceScopes) scopes else Sentry.getCurrentScopes()).options.dateProvider.now()

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

    on(SentryKtorClientPluginContextHook(scopes, pluginConfig.enabled)) { block -> block() }
  }

/**
 * Context hook to manage scopes during request handling. Forks the current scope and uses
 * [SentryContext] to ensure that the whole pipeline runs within the correct scopes.
 */
public open class SentryKtorClientPluginContextHook(
  protected val scopes: IScopes,
  protected val enabled: Boolean,
) : ClientHook<suspend (suspend () -> Unit) -> Unit> {
  private val phase = PipelinePhase("SentryKtorClientPluginContext")

  override fun install(client: HttpClient, handler: suspend (suspend () -> Unit) -> Unit) {
    if (!enabled) {
      return
    }

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
