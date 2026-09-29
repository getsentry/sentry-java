# sentry-android-cronet

Experimental, opt-in request tracing and HTTP breadcrumbs for [Cronet](https://developer.android.com/develop/connectivity/cronet).

## Usage

Add `io.sentry:sentry-android-cronet` using the same version as your Sentry Android SDK. Supply your own Cronet implementation; this module does not select or bundle an engine.

Wrap your existing callback once per request:

```kotlin
val url = "https://example.com/api"
val callback = SentryCronetCallback(url, "GET", applicationCallback)
val request = cronetEngine.newUrlRequestBuilder(url, callback, executor)
    .setHttpMethod("GET")
    .build()
callback.start(request) // Instead of request.start()
```

Construct the callback in the initiating Sentry scope. Pass the same URL and method used by the request builder, including `POST` when using an upload provider. Call `start` on the callback with that request, on the executor required by Cronet. Do not reuse a callback for multiple requests.

The callback starts one `http.client` child span under the active transaction when the request starts. It finishes the span and adds an HTTP breadcrumb on success, failure, or cancellation. Without an active transaction, it still adds a breadcrumb. Span data includes the HTTP method, response status, and Cronet's negotiated protocol (including `h3` when reported by Cronet). URL handling uses the same filtering utilities as the OkHttp integration.

Your callback still controls redirects and response-body reads. The span covers the original request through redirects and body consumption, and retains the original URL and method. It does not represent individual redirect hops. Terminal callbacks run after Sentry finishes the span. Exceptions from nonterminal callbacks are left to Cronet, which reports them through `onFailed`.

## Draft Scope

- No automatic engine instrumentation or bytecode instrumentation.
- No `sentry-trace`, `baggage`, or `traceparent` header injection. Redirect-safe propagation needs a separate design.
- No separate DNS, connect, or TLS spans, request/response body capture, or automatic failed-request error events.
- No support for `BidirectionalStream` or Android's separate `android.net.http` API.
- This draft has not been validated with a real Cronet engine or device. Provider compatibility and the public API need Android SDK team review before release.

Public Android integration documentation, release-registry registration, and the `.craft.yml` SDK entry are follow-ups before publishing this module.
