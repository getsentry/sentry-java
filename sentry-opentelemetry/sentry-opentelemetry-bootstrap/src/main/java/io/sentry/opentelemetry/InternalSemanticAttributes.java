package io.sentry.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;

// TODO [POTEL] context key vs attribute key
public final class InternalSemanticAttributes {
  //  public static final AttributeKey<String> ORIGIN = AttributeKey.stringKey("sentry.origin");
  //  public static final AttributeKey<String> OP = AttributeKey.stringKey("sentry.op");
  //  public static final AttributeKey<String> SOURCE = AttributeKey.stringKey("sentry.source");
  public static final AttributeKey<Boolean> SAMPLED = AttributeKey.booleanKey("sentry.sampled");
  public static final AttributeKey<Double> SAMPLE_RATE =
      AttributeKey.doubleKey("sentry.sample_rate");
  public static final AttributeKey<Boolean> PARENT_SAMPLED =
      AttributeKey.booleanKey("sentry.parent_sampled");
  public static final AttributeKey<Boolean> PROFILE_SAMPLED =
      AttributeKey.booleanKey("sentry.profile_sampled");
  public static final AttributeKey<Double> PROFILE_SAMPLE_RATE =
      AttributeKey.doubleKey("sentry.profile_sample_rate");
  public static final AttributeKey<Boolean> IS_REMOTE_PARENT =
      AttributeKey.booleanKey("sentry.is_remote_parent");
  //  public static final AttributeKey<String> BREADCRUMB_TYPE =
  //      AttributeKey.stringKey("sentry.breadcrumb.type");
  //  public static final AttributeKey<SentryLevel> BREADCRUMB_TYPE =
  // InternalAttributeKeyImpl.create("sentry.breadcrumb.type", SentryLevel.class);
  //  BREADCRUMB_TYPE("sentry.breadcrumb.type"),
  //  BREADCRUMB_LEVEL("sentry.breadcrumb.level"),
  //  BREADCRUMB_EVENT_ID("sentry.breadcrumb.event_id"),
  //  BREADCRUMB_CATEGORY("sentry.breadcrumb.category"),
  //  BREADCRUMB_DATA("sentry.breadcrumb.data");

}
