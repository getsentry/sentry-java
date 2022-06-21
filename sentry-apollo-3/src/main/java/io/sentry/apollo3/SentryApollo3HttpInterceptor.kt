package io.sentry.apollo3

import com.apollographql.apollo3.api.http.HttpRequest
import com.apollographql.apollo3.api.http.HttpResponse
import com.apollographql.apollo3.exception.ApolloNetworkException
import com.apollographql.apollo3.network.http.HttpInterceptor
import com.apollographql.apollo3.network.http.HttpInterceptorChain
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.HubAdapter
import io.sentry.IHub
import io.sentry.ISpan
import io.sentry.SentryLevel
import io.sentry.SpanStatus
import io.sentry.TracingOrigins
import io.sentry.TypeCheckHint
import okio.Buffer
import java.io.InputStreamReader

class SentryApollo3HttpInterceptor(private val hub: IHub = HubAdapter.getInstance(), private val beforeSpan: BeforeSpanCallback? = null) :
    HttpInterceptor {

    constructor(hub: IHub) : this(hub, null)
    constructor(beforeSpan: BeforeSpanCallback) : this(HubAdapter.getInstance(), beforeSpan)

    override suspend fun intercept(
        request: HttpRequest,
        chain: HttpInterceptorChain
    ): HttpResponse {
        val activeSpan = hub.span
        return if (activeSpan == null) {
            chain.proceed(request)
        } else {
            val span = startChild(request, activeSpan)

            val requestBuilder = request.newBuilder()

            if (TracingOrigins.contain(hub.options.tracingOrigins, request.url)) {
                val sentryTraceHeader = span.toSentryTrace()
                val baggageHeader = span.toBaggageHeader()
                requestBuilder.addHeader(sentryTraceHeader.name, sentryTraceHeader.value)

                baggageHeader?.let {
                    requestBuilder.addHeader(it.name, it.value)
                }
            }

            val modifiedRequest = requestBuilder.build()

            return try {
                val httpResponse = chain.proceed(modifiedRequest)
                span.status = SpanStatus.fromHttpStatusCode(httpResponse.statusCode, SpanStatus.UNKNOWN)
                finish(span, modifiedRequest, httpResponse)

                httpResponse
            } catch (e: Throwable) {
                when (e) {
                    is ApolloNetworkException -> span.status = SpanStatus.INTERNAL_ERROR
                    else -> SpanStatus.UNKNOWN
                }
                finish(span, modifiedRequest)
                throw e
            }
        }
    }

    private fun Long?.ifHasValidLength(fn: (Long) -> Unit) {
        if (this != null && this != -1L) {
            fn.invoke(this)
        }
    }

    private fun startChild(request: HttpRequest, activeSpan: ISpan): ISpan {
        val buffer = Buffer()
        request.body?.writeTo(buffer)

        val reader = InputStreamReader(buffer.inputStream())

        val content = try {
            hub.options.serializer.deserialize(
                reader,
                ApolloRequestBodyContent::class.java,
                ApolloRequestBodyContent.Deserializer
            )
        } catch (e: Throwable) {
            hub.options.logger.log(SentryLevel.ERROR, "Error deserializing Apollo Request Body.", e)
        }

        val span = when (content) {
            is ApolloRequestBodyContent -> createSpanFromBodyContent(activeSpan, request, content)
            else -> activeSpan.startChild("apollo.request.unknown")
        }

        val operationId = request.headers.firstOrNull { it.name == "X-APOLLO-OPERATION-ID" }?.value ?: "unknown"

        return span.apply {
            setData("operationId", operationId)
        }
    }

    private fun createSpanFromBodyContent(activeSpan: ISpan, request: HttpRequest, content: ApolloRequestBodyContent): ISpan {
        val operationType = parseOperationType(content)
        val operationName = content.operationName ?: request.headers.firstOrNull { it.name == "X-APOLLO-OPERATION-NAME" }?.value ?: "unknown"
        val description = "$operationType $operationName"

        return activeSpan.startChild(operationName, description).apply {
            setData("variables", content.variables.toString())
        }
    }

    private fun finish(span: ISpan, request: HttpRequest, response: HttpResponse? = null) {
        var newSpan: ISpan = span
        if (beforeSpan != null) {
            try {
                newSpan = beforeSpan.execute(span, request, response)
            } catch (e: Throwable) {
                hub.options.logger.log(SentryLevel.ERROR, "An error occurred while executing beforeSpan on ApolloInterceptor", e)
            }
        }
        newSpan.finish()

        response?.let { httpResponse ->
            val breadcrumb =
                Breadcrumb.http(request.url, request.method.name, httpResponse.statusCode)

            request.body?.contentLength.ifHasValidLength { contentLength ->
                breadcrumb.setData("request_body_size", contentLength)
            }

            httpResponse.body?.peek()?.readByteArray()?.size?.toLong().ifHasValidLength { contentLength ->
                breadcrumb.setData("response_body_size", contentLength)
            }

            val hint = Hint().also {
                it.set(TypeCheckHint.APOLLO_REQUEST, request)
                it.set(TypeCheckHint.APOLLO_RESPONSE, httpResponse)
            }

            hub.addBreadcrumb(breadcrumb, hint)
        }
    }

    private fun parseOperationType(content: ApolloRequestBodyContent): String {
        val operationPart = content.query.takeWhile { !it.isWhitespace() }
        return KNOWN_OPERATIONS.firstOrNull { it == operationPart } ?: "unknown"
    }

    companion object {
        val KNOWN_OPERATIONS = listOf("query", "mutation", "subscription")
    }

    /**
     * The BeforeSpan callback
     */
    fun interface BeforeSpanCallback {
        /**
         * Mutates span before being added.
         *
         * @param span the span to mutate or drop
         * @param request the Apollo request object
         * @param response the Apollo response object
         */
        fun execute(span: ISpan, request: HttpRequest, response: HttpResponse?): ISpan
    }
}
