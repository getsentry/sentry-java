package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface DataConvention {
  // Keys that should respect the span data conventions, as described in
  //  https://develop.sentry.dev/sdk/performance/span-data-conventions/
  String HTTP_QUERY_KEY = "http.query";
  String HTTP_FRAGMENT_KEY = "http.fragment";
  String HTTP_METHOD_KEY = "http.method";
  String HTTP_STATUS_CODE_KEY = "http.response.status_code";
  String HTTP_RESPONSE_CONTENT_LENGTH_KEY = "http.response_content_length";
  String BLOCKED_MAIN_THREAD_KEY = "blocked_main_thread";
  String CALL_STACK_KEY = "call_stack";
}
