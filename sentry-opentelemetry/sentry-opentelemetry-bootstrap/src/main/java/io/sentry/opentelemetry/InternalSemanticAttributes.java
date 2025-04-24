package io.sentry.opentelemetry;

import io.opentelemetry.api.common.AttributeKey;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class InternalSemanticAttributes {
  public static final AttributeKey<Boolean> SAMPLED = AttributeKey.booleanKey("sentry.sampled");
  public static final AttributeKey<Double> SAMPLE_RATE =
      AttributeKey.doubleKey("sentry.sample_rate");
  public static final AttributeKey<Double> SAMPLE_RAND =
      AttributeKey.doubleKey("sentry.sample_rand");
  public static final AttributeKey<Boolean> PARENT_SAMPLED =
      AttributeKey.booleanKey("sentry.parent_sampled");
  public static final AttributeKey<Boolean> PROFILE_SAMPLED =
      AttributeKey.booleanKey("sentry.profile_sampled");
  public static final AttributeKey<Double> PROFILE_SAMPLE_RATE =
      AttributeKey.doubleKey("sentry.profile_sample_rate");
  public static final AttributeKey<Boolean> IS_REMOTE_PARENT =
      AttributeKey.booleanKey("sentry.is_remote_parent");
  public static final AttributeKey<String> BAGGAGE = AttributeKey.stringKey("sentry.baggage");
  public static final AttributeKey<Boolean> BAGGAGE_MUTABLE =
      AttributeKey.booleanKey("sentry.baggage_mutable");
  public static final AttributeKey<Boolean> CREATED_VIA_SENTRY_API = AttributeKey.booleanKey("sentry.is_created_via_sentry_api");
}
