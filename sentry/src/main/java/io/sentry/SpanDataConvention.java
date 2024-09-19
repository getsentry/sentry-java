package io.sentry;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface SpanDataConvention {
  // Keys that should respect the span data conventions, as described in
  //  https://develop.sentry.dev/sdk/performance/span-data-conventions/
  String DB_SYSTEM_KEY = "db.system";
  String DB_NAME_KEY = "db.name";
  String HTTP_QUERY_KEY = "http.query";
  String HTTP_FRAGMENT_KEY = "http.fragment";
  String HTTP_METHOD_KEY = "http.request.method";
  String HTTP_STATUS_CODE_KEY = "http.response.status_code";
  String HTTP_RESPONSE_CONTENT_LENGTH_KEY = "http.response_content_length";
  String BLOCKED_MAIN_THREAD_KEY = "blocked_main_thread";
  String CALL_STACK_KEY = "call_stack";
  String THREAD_ID = "thread.id";
  String THREAD_NAME = "thread.name";
  String FRAMES_TOTAL = "frames.total";
  String FRAMES_SLOW = "frames.slow";
  String FRAMES_FROZEN = "frames.frozen";
  String FRAMES_DELAY = "frames.delay";
  String CONTRIBUTES_TTID = "ui.contributes_to_ttid";
  String CONTRIBUTES_TTFD = "ui.contributes_to_ttfd";
  String HTTP_START_TIMESTAMP = "http.start_timestamp";
  String HTTP_END_TIMESTAMP = "http.end_timestamp";
}
