package io.sentry;

import static io.sentry.protocol.Contexts.REPLAY_ID;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.TransactionNameSource;
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

  public static final @NotNull Baggage NOOP =
      new Baggage(new HashMap<>(), null, false, true, NoOpLogger.getInstance());
  static final @NotNull String CHARSET = "UTF-8";
  static final @NotNull Integer MAX_BAGGAGE_STRING_LENGTH = 8192;
  static final @NotNull Integer MAX_BAGGAGE_LIST_MEMBER_COUNT = 64;
  static final @NotNull String SENTRY_BAGGAGE_PREFIX = "sentry-";

  final @NotNull Map<String, String> keyValues;
  final @Nullable String thirdPartyHeader;
  private boolean mutable;
  private boolean shouldFreeze;
  final @NotNull ILogger logger;

  @NotNull
  public static Baggage fromHeader(final @Nullable String headerValue) {
    return Baggage.fromHeader(
        headerValue, false, ScopesAdapter.getInstance().getOptions().getLogger());
  }

  @NotNull
  public static Baggage fromHeader(final @Nullable List<String> headerValues) {
    return Baggage.fromHeader(
        headerValues, false, ScopesAdapter.getInstance().getOptions().getLogger());
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
    boolean shouldFreeze = false;

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
              if (!DSCKeys.SAMPLE_RAND.equalsIgnoreCase(key)) {
                shouldFreeze = true;
              }
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
    /*
     can't freeze Baggage right away as we might have to backfill sampleRand
     also we don't receive sentry-trace header here or in ctor so we can't
     backfill then freeze here unless we pass sentry-trace header.
    */
    return new Baggage(keyValues, thirdPartyHeader, true, shouldFreeze, logger);
  }

  @ApiStatus.Internal
  @NotNull
  public static Baggage fromEvent(
      final @NotNull SentryEvent event, final @NotNull SentryOptions options) {
    final Baggage baggage = new Baggage(options.getLogger());
    final SpanContext trace = event.getContexts().getTrace();
    baggage.setTraceId(trace != null ? trace.getTraceId().toString() : null);
    baggage.setPublicKey(options.retrieveParsedDsn().getPublicKey());
    baggage.setRelease(event.getRelease());
    baggage.setEnvironment(event.getEnvironment());
    baggage.setTransaction(event.getTransaction());
    // we don't persist sample rate
    baggage.setSampleRate(null);
    baggage.setSampled(null);
    baggage.setSampleRand(null);
    final @Nullable Object replayId = event.getContexts().get(REPLAY_ID);
    if (replayId != null && !replayId.toString().equals(SentryId.EMPTY_ID.toString())) {
      baggage.setReplayId(replayId.toString());
      // relay will set it from the DSC, we don't need to send it
      event.getContexts().remove(REPLAY_ID);
    }
    baggage.freeze();
    return baggage;
  }

  @ApiStatus.Internal
  public Baggage(final @NotNull ILogger logger) {
    this(new HashMap<>(), null, true, false, logger);
  }

  @ApiStatus.Internal
  public Baggage(final @NotNull Baggage baggage) {
    this(
        baggage.keyValues,
        baggage.thirdPartyHeader,
        baggage.mutable,
        baggage.shouldFreeze,
        baggage.logger);
  }

  @ApiStatus.Internal
  @SuppressWarnings("ObjectToString")
  public Baggage(
      final @NotNull Map<String, String> keyValues,
      final @Nullable String thirdPartyHeader,
      boolean isMutable,
      boolean shouldFreeze,
      final @NotNull ILogger logger) {
    this.keyValues = keyValues;
    this.logger = logger;
    this.thirdPartyHeader = thirdPartyHeader;
    this.mutable = isMutable;
    this.shouldFreeze = shouldFreeze;
  }

  @SuppressWarnings("ObjectToString")
  @ApiStatus.Internal
  public void freeze() {
    this.mutable = false;
  }

  @ApiStatus.Internal
  public boolean isMutable() {
    return mutable;
  }

  @ApiStatus.Internal
  public boolean isShouldFreeze() {
    return shouldFreeze;
  }

  @Nullable
  public String getThirdPartyHeader() {
    return thirdPartyHeader;
  }

  @SuppressWarnings("ObjectToString")
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
  public @Nullable String getSampled() {
    return get(DSCKeys.SAMPLED);
  }

  @ApiStatus.Internal
  public void setSampleRate(final @Nullable String sampleRate) {
    set(DSCKeys.SAMPLE_RATE, sampleRate);
  }

  @ApiStatus.Internal
  public @Nullable String getSampleRand() {
    return get(DSCKeys.SAMPLE_RAND);
  }

  @ApiStatus.Internal
  public void setSampleRand(final @Nullable String sampleRand) {
    set(DSCKeys.SAMPLE_RAND, sampleRand);
  }

  @ApiStatus.Internal
  public void setSampleRandDouble(final @Nullable Double sampleRand) {
    setSampleRand(sampleRateToString(sampleRand));
  }

  @ApiStatus.Internal
  public void setSampled(final @Nullable String sampled) {
    set(DSCKeys.SAMPLED, sampled);
  }

  @ApiStatus.Internal
  public @Nullable String getReplayId() {
    return get(DSCKeys.REPLAY_ID);
  }

  @ApiStatus.Internal
  public void setReplayId(final @Nullable String replayId) {
    set(DSCKeys.REPLAY_ID, replayId);
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
      final @NotNull SentryId traceId,
      final @Nullable SentryId replayId,
      final @NotNull SentryOptions sentryOptions,
      final @Nullable TracesSamplingDecision samplingDecision,
      final @Nullable String transactionName,
      final @Nullable TransactionNameSource transactionNameSource) {
    setTraceId(traceId.toString());
    setPublicKey(sentryOptions.retrieveParsedDsn().getPublicKey());
    setRelease(sentryOptions.getRelease());
    setEnvironment(sentryOptions.getEnvironment());
    setTransaction(isHighQualityTransactionName(transactionNameSource) ? transactionName : null);
    if (replayId != null && !SentryId.EMPTY_ID.equals(replayId)) {
      setReplayId(replayId.toString());
    }
    setSampleRate(sampleRateToString(sampleRate(samplingDecision)));
    setSampled(StringUtils.toString(sampled(samplingDecision)));
  }

  @ApiStatus.Internal
  public void setValuesFromScope(
      final @NotNull IScope scope, final @NotNull SentryOptions options) {
    final @NotNull PropagationContext propagationContext = scope.getPropagationContext();
    final @NotNull SentryId replayId = scope.getReplayId();
    setTraceId(propagationContext.getTraceId().toString());
    setPublicKey(options.retrieveParsedDsn().getPublicKey());
    setRelease(options.getRelease());
    setEnvironment(options.getEnvironment());
    if (!SentryId.EMPTY_ID.equals(replayId)) {
      setReplayId(replayId.toString());
    }
    setTransaction(null);
    setSampleRate(null);
    setSampled(null);
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

  private static @Nullable Boolean sampled(@Nullable TracesSamplingDecision samplingDecision) {
    if (samplingDecision == null) {
      return null;
    }

    return samplingDecision.getSampled();
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
  public @Nullable Double getSampleRandDouble() {
    final String sampleRandString = getSampleRand();
    if (sampleRandString != null) {
      try {
        double sampleRand = Double.parseDouble(sampleRandString);
        if (SampleRateUtils.isValidTracesSampleRate(sampleRand, false)) {
          return sampleRand;
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
    final String replayIdString = getReplayId();
    final String publicKey = getPublicKey();

    if (traceIdString != null && publicKey != null) {
      final @NotNull TraceContext traceContext =
          new TraceContext(
              new SentryId(traceIdString),
              publicKey,
              getRelease(),
              getEnvironment(),
              getUserId(),
              getTransaction(),
              getSampleRate(),
              getSampled(),
              replayIdString == null ? null : new SentryId(replayIdString),
              getSampleRand());
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
    public static final String TRANSACTION = "sentry-transaction";
    public static final String SAMPLE_RATE = "sentry-sample_rate";
    public static final String SAMPLE_RAND = "sentry-sample_rand";
    public static final String SAMPLED = "sentry-sampled";
    public static final String REPLAY_ID = "sentry-replay_id";

    public static final List<String> ALL =
        Arrays.asList(
            TRACE_ID,
            PUBLIC_KEY,
            RELEASE,
            USER_ID,
            ENVIRONMENT,
            TRANSACTION,
            SAMPLE_RATE,
            SAMPLE_RAND,
            SAMPLED,
            REPLAY_ID);
  }
}
