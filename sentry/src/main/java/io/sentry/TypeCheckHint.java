package io.sentry;

import org.jetbrains.annotations.ApiStatus;

/** Constants used for Type Check hints. */
public final class TypeCheckHint {

  @ApiStatus.Internal public static final String SENTRY_TYPE_CHECK_HINT = "sentry:typeCheckHint";

  @ApiStatus.Internal
  public static final String SENTRY_IS_FROM_HYBRID_SDK = "sentry:isFromHybridSdk";

  @ApiStatus.Internal
  public static final String SENTRY_EVENT_DROP_REASON = "sentry:eventDropReason";

  @ApiStatus.Internal
  public static final String SENTRY_REPLAY_NETWORK_DETAILS = "sentry:replayNetworkDetails";

  @ApiStatus.Internal public static final String SENTRY_JAVASCRIPT_SDK_NAME = "sentry.javascript";

  @ApiStatus.Internal public static final String SENTRY_DOTNET_SDK_NAME = "sentry.dotnet";

  @ApiStatus.Internal public static final String SENTRY_DART_SDK_NAME = "sentry.dart";

  /** Used for Synthetic exceptions. */
  public static final String SENTRY_SYNTHETIC_EXCEPTION = "syntheticException";

  /** Used for Activity breadcrumbs. */
  public static final String ANDROID_ACTIVITY = "android:activity";

  /** Used for Configuration changes breadcrumbs. */
  public static final String ANDROID_CONFIGURATION = "android:configuration";

  /** Used for System breadcrumbs. */
  public static final String ANDROID_INTENT = "android:intent";

  /** Used for Sensor breadcrumbs. */
  public static final String ANDROID_SENSOR_EVENT = "android:sensorEvent";

  /** Used for Gesture breadcrumbs. */
  public static final String ANDROID_MOTION_EVENT = "android:motionEvent";

  /** Used for View breadcrumbs. */
  public static final String ANDROID_VIEW = "android:view";

  /** Used for Fragment breadcrumbs. */
  public static final String ANDROID_FRAGMENT = "android:fragment";

  /** Used for Navigation breadrcrumbs. */
  public static final String ANDROID_NAV_DESTINATION = "android:navigationDestination";

  /** Used for Network breadrcrumbs. */
  public static final String ANDROID_NETWORK_CAPABILITIES = "android:networkCapabilities";

  /** Used for OkHttp response breadcrumbs. */
  public static final String OKHTTP_RESPONSE = "okHttp:response";

  /** Used for OkHttp Request breadcrumbs. */
  public static final String OKHTTP_REQUEST = "okHttp:request";

  /** Used for Apollo response breadcrumbs. */
  public static final String APOLLO_RESPONSE = "apollo:response";

  /** Used for Apollo Request breadcrumbs. */
  public static final String APOLLO_REQUEST = "apollo:request";

  /** Used for GraphQl handler exceptions. */
  public static final String GRAPHQL_HANDLER_PARAMETERS = "graphql:handlerParameters";

  /** Used for GraphQl data fetcher breadcrumbs. */
  public static final String GRAPHQL_DATA_FETCHING_ENVIRONMENT = "graphql:dataFetchingEnvironment";

  /** Used for JUL breadcrumbs. */
  public static final String JUL_LOG_RECORD = "jul:logRecord";

  /** Used for Log4j breadcrumbs. */
  public static final String LOG4J_LOG_EVENT = "log4j:logEvent";

  /** Used for Logback breadcrumbs. */
  public static final String LOGBACK_LOGGING_EVENT = "logback:loggingEvent";

  /** Used for OpenFeign response breadcrumbs. */
  public static final String OPEN_FEIGN_RESPONSE = "openFeign:response";

  /** Used for OpenFeign Request breadcrumbs. */
  public static final String OPEN_FEIGN_REQUEST = "openFeign:request";

  /** Used for Servlet Request breadcrumbs. */
  public static final String SERVLET_REQUEST = "servlet:request";

  /** Used for Spring resolver exceptions. */
  public static final String SPRING_RESOLVER_RESPONSE = "springResolver:response";

  /** Used for Spring resolver exceptions. */
  public static final String SPRING_RESOLVER_REQUEST = "springResolver:request";

  /** Used for Spring request filter breadcrumbs. */
  public static final String SPRING_REQUEST_FILTER_RESPONSE = "springRequestFilter:response";

  /** Used for Spring request filter breadcrumbs. */
  public static final String SPRING_REQUEST_FILTER_REQUEST = "springRequestFilter:request";

  /** Used for Spring request interceptor breadcrumbs. */
  public static final String SPRING_REQUEST_INTERCEPTOR_RESPONSE =
      "springRequestInterceptor:response";

  /** Used for Spring request interceptor breadcrumbs. */
  public static final String SPRING_REQUEST_INTERCEPTOR_REQUEST =
      "springRequestInterceptor:request";

  /** Used for Spring request interceptor breadcrumbs. */
  public static final String SPRING_REQUEST_INTERCEPTOR_REQUEST_BODY =
      "springRequestInterceptor:requestBody";

  /** Used for Spring WebFlux exception handler. */
  public static final String WEBFLUX_EXCEPTION_HANDLER_RESPONSE =
      "webFluxExceptionHandler:response";

  /** Used for Spring WebFlux exception handler. */
  public static final String WEBFLUX_EXCEPTION_HANDLER_REQUEST = "webFluxExceptionHandler:request";

  /** Used for Spring WebFlux exception handler. */
  public static final String WEBFLUX_EXCEPTION_HANDLER_EXCHANGE =
      "webFluxExceptionHandler:exchange";

  /** Used for Spring WebFlux filter breadcrumbs. */
  public static final String WEBFLUX_FILTER_RESPONSE = "webFluxFilter:response";

  /** Used for Spring WebFlux filter breadcrumbs. */
  public static final String WEBFLUX_FILTER_REQUEST = "webFluxFilter:request";

  /** Used for Spring exchange filter breadcrumbs. */
  public static final String SPRING_EXCHANGE_FILTER_RESPONSE = "springExchangeFilter:response";

  /** Used for Spring exchange filter breadcrumbs. */
  public static final String SPRING_EXCHANGE_FILTER_REQUEST = "springExchangeFilter:request";

  /** Used for Ktor response breadcrumbs. */
  public static final String KTOR_CLIENT_RESPONSE = "ktorClient:response";

  /** Used for Ktor Request breadcrumbs. */
  public static final String KTOR_CLIENT_REQUEST = "ktorClient:request";
}
