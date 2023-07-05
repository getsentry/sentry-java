package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.protocol.User;
import io.sentry.util.SampleRateUtils;
import io.sentry.util.StringUtils;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class Baggage {

  static final @NotNull String CHARSET = "UTF-8";
  static final @NotNull Integer MAX_BAGGAGE_STRING_LENGTH = 8192;
  static final @NotNull Integer MAX_BAGGAGE_LIST_MEMBER_COUNT = 64;
  static final @NotNull String SENTRY_BAGGAGE_PREFIX = "sentry-";

  final @NotNull Map<String, String> keyValues;
  final @Nullable String thirdPartyHeader;
  private boolean mutable;
  final @NotNull ILogger logger;

  @NotNull
  public static Baggage fromHeader(final @Nullable String headerValue) {
    return Baggage.fromHeader(
        headerValue, false, HubAdapter.getInstance().getOptions().getLogger());
  }

  @NotNull
  public static Baggage fromHeader(final @Nullable List<String> headerValues) {
    return Baggage.fromHeader(
        headerValues, false, HubAdapter.getInstance().getOptions().getLogger());
  }

  @ApiStatus.Internal
  @NotNull
  public static Baggage fromHeader(final String headerValue, final @NotNull ILogger logger) {
    return Baggage.fromHeader(headerValue, false, logger);
  }

  @ApiStatus.Internal
  @NotNull
  public static Baggage fromHeader(
      final @Nullable List<String> headerValues, final @NotNull ILogger logger) {
    return Baggage.fromHeader(headerValues, false, logger);
  }

  @ApiStatus.Internal
  @NotNull
  public static Baggage fromHeader(
      final @Nullable List<String> headerValues,
      final boolean includeThirdPartyValues,
      final @NotNull ILogger logger) {

    if (headerValues != null) {
      return Baggage.fromHeader(
          StringUtils.join(",", headerValues), includeThirdPartyValues, logger);
    } else {
      return Baggage.fromHeader((String) null, includeThirdPartyValues, logger);
    }
  }

  @ApiStatus.Internal
  @NotNull
  public static Baggage fromHeader(
      final @Nullable String headerValue,
      final boolean includeThirdPartyValues,
      final @NotNull ILogger logger) {
    final @NotNull Map<String, String> keyValues = new HashMap<>();
    final @NotNull List<String> thirdPartyKeyValueStrings = new ArrayList<>();
    boolean mutable = true;

    if (headerValue != null) {
      try {
        // see https://errorprone.info/bugpattern/StringSplitter for why limit is passed
        final String[] keyValueStrings = headerValue.split(",", -1);
        for (final String keyValueString : keyValueStrings) {
          if (keyValueString.trim().startsWith(SENTRY_BAGGAGE_PREFIX)) {
            try {
              // As per spec (https://www.w3.org/TR/baggage/#value)
              // a value may contain "=" signs, thus we split by the first occurrence
              final int separatorIndex = keyValueString.indexOf("=");
              final String key = keyValueString.substring(0, separatorIndex).trim();
              final String keyDecoded = decode(key);
              final String value = keyValueString.substring(separatorIndex + 1).trim();
              final String valueDecoded = decode(value);

              keyValues.put(keyDecoded, valueDecoded);
              mutable = false;
            } catch (Throwable e) {
              logger.log(
                  SentryLevel.ERROR,
                  e,
                  "Unable to decode baggage key value pair %s",
                  keyValueString);
            }
          } else if (includeThirdPartyValues) {
            thirdPartyKeyValueStrings.add(keyValueString.trim());
          }
        }
      } catch (Throwable e) {
        logger.log(SentryLevel.ERROR, e, "Unable to decode baggage header %s", headerValue);
      }
    }
    final String thirdPartyHeader =
        thirdPartyKeyValueStrings.isEmpty()
            ? null
            : StringUtils.join(",", thirdPartyKeyValueStrings);
    return new Baggage(keyValues, thirdPartyHeader, mutable, logger);
  }

  @ApiStatus.Internal
  public Baggage(final @NotNull ILogger logger) {
    this(new HashMap<>(), null, true, logger);
  }

  @ApiStatus.Internal
  public Baggage(final @NotNull Baggage baggage) {
    this(baggage.keyValues, baggage.thirdPartyHeader, baggage.mutable, baggage.logger);
  }

  @ApiStatus.Internal
  public Baggage(
      final @NotNull Map<String, String> keyValues,
      final @Nullable String thirdPartyHeader,
      boolean isMutable,
      final @NotNull ILogger logger) {
    this.keyValues = keyValues;
    this.logger = logger;
    this.mutable = isMutable;
    this.thirdPartyHeader = thirdPartyHeader;
  }

  @ApiStatus.Internal
  public void freeze() {
    this.mutable = false;
  }

  @ApiStatus.Internal
  public boolean isMutable() {
    return mutable;
  }

  @Nullable
  public String getThirdPartyHeader() {
    return thirdPartyHeader;
  }

  public @NotNull String toHeaderString(@Nullable String thirdPartyBaggageHeaderString) {
    final StringBuilder sb = new StringBuilder();
    String separator = "";
    int listMemberCount = 0;

    if (thirdPartyBaggageHeaderString != null && !thirdPartyBaggageHeaderString.isEmpty()) {
      sb.append(thirdPartyBaggageHeaderString);
      listMemberCount = StringUtils.countOf(thirdPartyBaggageHeaderString, ',') + 1;
      separator = ",";
    }

    final Set<String> keys = new TreeSet<>(keyValues.keySet());
    for (final String key : keys) {
      final @Nullable String value = keyValues.get(key);

      if (value != null) {
        if (listMemberCount >= MAX_BAGGAGE_LIST_MEMBER_COUNT) {
          logger.log(
              SentryLevel.ERROR,
              "Not adding baggage value %s as the total number of list members would exceed the maximum of %s.",
              key,
              MAX_BAGGAGE_LIST_MEMBER_COUNT);
        } else {
          try {
            final String encodedKey = encode(key);
            final String encodedValue = encode(value);
            final String encodedKeyValue = separator + encodedKey + "=" + encodedValue;

            final int valueLength = encodedKeyValue.length();
            final int totalLengthIfValueAdded = sb.length() + valueLength;
            if (totalLengthIfValueAdded > MAX_BAGGAGE_STRING_LENGTH) {
              logger.log(
                  SentryLevel.ERROR,
                  "Not adding baggage value %s as the total header value length would exceed the maximum of %s.",
                  key,
                  MAX_BAGGAGE_STRING_LENGTH);
            } else {
              listMemberCount++;
              sb.append(encodedKeyValue);
              separator = ",";
            }
          } catch (Throwable e) {
            logger.log(
                SentryLevel.ERROR,
                e,
                "Unable to encode baggage key value pair (key=%s,value=%s).",
                key,
                value);
          }
        }
      }
    }

    return sb.toString();
  }

  private String encode(final @NotNull String value) throws UnsupportedEncodingException {
    return URLEncoder.encode(value, CHARSET).replaceAll("\\+", "%20");
  }

  private static String decode(final @NotNull String value) throws UnsupportedEncodingException {
    return URLDecoder.decode(value, CHARSET);
  }

  @ApiStatus.Internal
  public @Nullable String get(final @Nullable String key) {
    if (key == null) {
      return null;
    }

    return keyValues.get(key);
  }

  @ApiStatus.Internal
  public @Nullable String getTraceId() {
    return get(DSCKeys.TRACE_ID);
  }

  @ApiStatus.Internal
  public void setTraceId(final @Nullable String traceId) {
    set(DSCKeys.TRACE_ID, traceId);
  }

  @ApiStatus.Internal
  public @Nullable String getPublicKey() {
    return get(DSCKeys.PUBLIC_KEY);
  }

  @ApiStatus.Internal
  public void setPublicKey(final @Nullable String publicKey) {
    set(DSCKeys.PUBLIC_KEY, publicKey);
  }

  @ApiStatus.Internal
  public @Nullable String getEnvironment() {
    return get(DSCKeys.ENVIRONMENT);
  }

  @ApiStatus.Internal
  public void setEnvironment(final @Nullable String environment) {
    set(DSCKeys.ENVIRONMENT, environment);
  }

  @ApiStatus.Internal
  public @Nullable String getRelease() {
    return get(DSCKeys.RELEASE);
  }

  @ApiStatus.Internal
  public void setRelease(final @Nullable String release) {
    set(DSCKeys.RELEASE, release);
  }

  @ApiStatus.Internal
  public @Nullable String getUserId() {
    return get(DSCKeys.USER_ID);
  }

  @ApiStatus.Internal
  public void setUserId(final @Nullable String userId) {
    set(DSCKeys.USER_ID, userId);
  }

  @ApiStatus.Internal
  public @Nullable String getUserSegment() {
    return get(DSCKeys.USER_SEGMENT);
  }

  @ApiStatus.Internal
  public void setUserSegment(final @Nullable String userSegment) {
    set(DSCKeys.USER_SEGMENT, userSegment);
  }

  @ApiStatus.Internal
  public @Nullable String getTransaction() {
    return get(DSCKeys.TRANSACTION);
  }

  @ApiStatus.Internal
  public void setTransaction(final @Nullable String transaction) {
    set(DSCKeys.TRANSACTION, transaction);
  }

  @ApiStatus.Internal
  public @Nullable String getSampleRate() {
    return get(DSCKeys.SAMPLE_RATE);
  }

  @ApiStatus.Internal
  public void setSampleRate(final @Nullable String sampleRate) {
    set(DSCKeys.SAMPLE_RATE, sampleRate);
  }

  @ApiStatus.Internal
  public void set(final @NotNull String key, final @Nullable String value) {
    if (mutable) {
      this.keyValues.put(key, value);
    }
  }

  @ApiStatus.Internal
  public @NotNull Map<String, Object> getUnknown() {
    final @NotNull Map<String, Object> unknown = new ConcurrentHashMap<>();
    for (Map.Entry<String, String> keyValue : this.keyValues.entrySet()) {
      final @NotNull String key = keyValue.getKey();
      final @Nullable String value = keyValue.getValue();
      if (!DSCKeys.ALL.contains(key)) {
        if (value != null) {
          final @NotNull String unknownKey = key.replaceFirst(SENTRY_BAGGAGE_PREFIX, "");
          unknown.put(unknownKey, value);
        }
      }
    }

    return unknown;
  }

  @ApiStatus.Internal
  public void setValuesFromTransaction(
      final @NotNull ITransaction transaction,
      final @Nullable User user,
      final @NotNull SentryOptions sentryOptions,
      final @Nullable TracesSamplingDecision samplingDecision) {
    setTraceId(transaction.getSpanContext().getTraceId().toString());
    setPublicKey(new Dsn(sentryOptions.getDsn()).getPublicKey());
    setRelease(sentryOptions.getRelease());
    setEnvironment(sentryOptions.getEnvironment());
    setUserSegment(user != null ? getSegment(user) : null);
    setTransaction(
        isHighQualityTransactionName(transaction.getTransactionNameSource())
            ? transaction.getName()
            : null);
    setSampleRate(sampleRateToString(sampleRate(samplingDecision)));
  }

  @ApiStatus.Internal
  public void setValuesFromScope(final @NotNull Scope scope, final @NotNull SentryOptions options) {
    final @NotNull PropagationContext propagationContext = scope.getPropagationContext();
    final @Nullable User user = scope.getUser();
    setTraceId(propagationContext.getTraceId().toString());
    setPublicKey(new Dsn(options.getDsn()).getPublicKey());
    setRelease(options.getRelease());
    setEnvironment(options.getEnvironment());
    setUserSegment(user != null ? getSegment(user) : null);
    setTransaction(null);
    setSampleRate(null);
  }

  private static @Nullable String getSegment(final @NotNull User user) {
    if (user.getSegment() != null) {
      return user.getSegment();
    }

    final Map<String, String> userData = user.getData();
    if (userData != null) {
      return userData.get("segment");
    } else {
      return null;
    }
  }

  private static @Nullable Double sampleRate(@Nullable TracesSamplingDecision samplingDecision) {
    if (samplingDecision == null) {
      return null;
    }

    return samplingDecision.getSampleRate();
  }

  private static @Nullable String sampleRateToString(@Nullable Double sampleRateAsDouble) {
    if (!SampleRateUtils.isValidTracesSampleRate(sampleRateAsDouble, false)) {
      return null;
    }

    DecimalFormat df =
        new DecimalFormat("#.################", DecimalFormatSymbols.getInstance(Locale.ROOT));
    return df.format(sampleRateAsDouble);
  }

  private static boolean isHighQualityTransactionName(
      @Nullable TransactionNameSource transactionNameSource) {
    return transactionNameSource != null
        && !TransactionNameSource.URL.equals(transactionNameSource);
  }

  @ApiStatus.Internal
  public @Nullable Double getSampleRateDouble() {
    final String sampleRateString = getSampleRate();
    if (sampleRateString != null) {
      try {
        double sampleRate = Double.parseDouble(sampleRateString);
        if (SampleRateUtils.isValidTracesSampleRate(sampleRate, false)) {
          return sampleRate;
        }
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  @ApiStatus.Internal
  @Nullable
  public TraceContext toTraceContext() {
    final String traceIdString = getTraceId();
    final String publicKey = getPublicKey();

    if (traceIdString != null && publicKey != null) {
      final @NotNull TraceContext traceContext =
          new TraceContext(
              new SentryId(traceIdString),
              publicKey,
              getRelease(),
              getEnvironment(),
              getUserId(),
              getUserSegment(),
              getTransaction(),
              getSampleRate());
      traceContext.setUnknown(getUnknown());
      return traceContext;
    } else {
      return null;
    }
  }

  @ApiStatus.Internal
  public static final class DSCKeys {
    public static final String TRACE_ID = "sentry-trace_id";
    public static final String PUBLIC_KEY = "sentry-public_key";
    public static final String RELEASE = "sentry-release";
    public static final String USER_ID = "sentry-user_id";
    public static final String ENVIRONMENT = "sentry-environment";
    public static final String USER_SEGMENT = "sentry-user_segment";
    public static final String TRANSACTION = "sentry-transaction";
    public static final String SAMPLE_RATE = "sentry-sample_rate";

    public static final List<String> ALL =
        Arrays.asList(
            TRACE_ID,
            PUBLIC_KEY,
            RELEASE,
            USER_ID,
            ENVIRONMENT,
            USER_SEGMENT,
            TRANSACTION,
            SAMPLE_RATE);
  }
}
