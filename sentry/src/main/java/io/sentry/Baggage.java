package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
import io.sentry.protocol.User;
import io.sentry.util.SampleRateUtil;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class Baggage {

  static final @NotNull String CHARSET = "UTF-8";
  static final @NotNull Integer MAX_BAGGAGE_STRING_LENGTH = 8192;
  static final @NotNull String SENTRY_BAGGAGE_PREFIX = "sentry-";

  final @NotNull Map<String, String> keyValues;
  final @Nullable String thirdPartyHeader;
  private boolean mutable;
  final @NotNull ILogger logger;

  @NotNull
  public static Baggage fromHeader(
      final @Nullable List<String> headerValues, final @NotNull ILogger logger) {
    return Baggage.fromHeader(headerValues, false, logger);
  }

  @NotNull
  public static Baggage fromHeader(
      final @Nullable List<String> headerValues,
      final boolean includeThirdPartyValues,
      final @NotNull ILogger logger) {

    if (headerValues != null) {
      return Baggage.fromHeader(String.join(",", headerValues), includeThirdPartyValues, logger);
    } else {
      return Baggage.fromHeader((String) null, includeThirdPartyValues, logger);
    }
  }

  @NotNull
  public static Baggage fromHeader(final String headerValue, final @NotNull ILogger logger) {
    return Baggage.fromHeader(headerValue, false, logger);
  }

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
        thirdPartyKeyValueStrings.isEmpty() ? null : String.join(",", thirdPartyKeyValueStrings);
    return new Baggage(keyValues, thirdPartyHeader, mutable, logger);
  }

  public Baggage(final @NotNull ILogger logger) {
    this(new HashMap<>(), null, true, logger);
  }

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

  public void freeze() {
    this.mutable = false;
  }

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

    if (thirdPartyBaggageHeaderString != null && !thirdPartyBaggageHeaderString.isEmpty()) {
      sb.append(thirdPartyBaggageHeaderString);
      separator = ",";
    }

    final Set<String> keys = new TreeSet<>(keyValues.keySet());
    for (final String key : keys) {
      final @Nullable String value = keyValues.get(key);

      if (value != null) {
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

    return sb.toString();
  }

  private String encode(final @NotNull String value) throws UnsupportedEncodingException {
    return URLEncoder.encode(value, CHARSET).replaceAll("\\+", "%20");
  }

  private static String decode(final @NotNull String value) throws UnsupportedEncodingException {
    return URLDecoder.decode(value, CHARSET);
  }

  public @Nullable String get(final @Nullable String key) {
    if (key == null) {
      return null;
    }

    return keyValues.get(key);
  }

  public @Nullable String getTraceId() {
    return get(DSCKeys.TRACE_ID);
  }

  public void setTraceId(final @Nullable String traceId) {
    set(DSCKeys.TRACE_ID, traceId);
  }

  public @Nullable String getPublicKey() {
    return get(DSCKeys.PUBLIC_KEY);
  }

  public void setPublicKey(final @Nullable String publicKey) {
    set(DSCKeys.PUBLIC_KEY, publicKey);
  }

  public @Nullable String getEnvironment() {
    return get(DSCKeys.ENVIRONMENT);
  }

  public void setEnvironment(final @Nullable String environment) {
    set(DSCKeys.ENVIRONMENT, environment);
  }

  public @Nullable String getRelease() {
    return get(DSCKeys.RELEASE);
  }

  public void setRelease(final @Nullable String release) {
    set(DSCKeys.RELEASE, release);
  }

  public @Nullable String getUserId() {
    return get(DSCKeys.USER_ID);
  }

  public void setUserId(final @Nullable String userId) {
    set(DSCKeys.USER_ID, userId);
  }

  public @Nullable String getUserSegment() {
    return get(DSCKeys.USER_SEGMENT);
  }

  public void setUserSegment(final @Nullable String userSegment) {
    set(DSCKeys.USER_SEGMENT, userSegment);
  }

  public @Nullable String getTransaction() {
    return get(DSCKeys.TRANSACTION);
  }

  public void setTransaction(final @Nullable String transaction) {
    set(DSCKeys.TRANSACTION, transaction);
  }

  public @Nullable String getSampleRate() {
    return get(DSCKeys.SAMPLE_RATE);
  }

  public void setSampleRate(final @Nullable String sampleRate) {
    set(DSCKeys.SAMPLE_RATE, sampleRate);
  }

  public void set(final @NotNull String key, final @Nullable String value) {
    if (mutable) {
      this.keyValues.put(key, value);
    }
  }

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

  private static @Nullable String getSegment(final @NotNull User user) {
    final Map<String, String> others = user.getOthers();
    if (others != null) {
      return others.get("segment");
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
    if (!SampleRateUtil.isValidTracesSampleRate(sampleRateAsDouble, false)) {
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

  public @Nullable Double getSampleRateDouble() {
    final String sampleRateString = getSampleRate();
    if (sampleRateString != null) {
      try {
        double sampleRate = Double.parseDouble(sampleRateString);
        if (SampleRateUtil.isValidTracesSampleRate(sampleRate, false)) {
          return sampleRate;
        }
      } catch (NumberFormatException e) {
        return null;
      }
    }
    return null;
  }

  @Nullable
  public TraceContext toTraceContext() {
    final String traceIdString = getTraceId();
    final String publicKey = getPublicKey();

    if (traceIdString != null && publicKey != null) {
      return new TraceContext(
          new SentryId(traceIdString),
          publicKey,
          getRelease(),
          getEnvironment(),
          getUserId(),
          getUserSegment(),
          getTransaction(),
          getSampleRate());
    } else {
      return null;
    }
  }

  public static final class DSCKeys {
    public static final String TRACE_ID = "sentry-trace_id";
    public static final String PUBLIC_KEY = "sentry-public_key";
    public static final String RELEASE = "sentry-release";
    public static final String USER_ID = "sentry-user_id";
    public static final String ENVIRONMENT = "sentry-environment";
    public static final String USER_SEGMENT = "sentry-user_segment";
    public static final String TRANSACTION = "sentry-transaction";
    public static final String SAMPLE_RATE = "sentry-sample_rate";
  }
}
