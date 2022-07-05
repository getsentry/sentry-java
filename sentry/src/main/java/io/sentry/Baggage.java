package io.sentry;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
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
  static final @NotNull Integer MAX_BAGGAGE_LIST_MEMBER_COUNT = 64;

  final @NotNull Map<String, String> keyValues;
  final @NotNull ILogger logger;

  public static Baggage fromHeader(
      final @Nullable List<String> headerValues, final @NotNull ILogger logger) {
    final Map<String, String> keyValues = new HashMap<>();

    if (headerValues != null) {
      for (final @Nullable String headerValue : headerValues) {
        final Map<String, String> keyValuesToAdd =
            extractKeyValuesFromBaggageString(headerValue, logger);
        keyValues.putAll(keyValuesToAdd);
      }
    }

    return new Baggage(keyValues, logger);
  }

  public static Baggage fromHeader(
      final @Nullable String headerValue, final @NotNull ILogger logger) {
    final Map<String, String> keyValues = extractKeyValuesFromBaggageString(headerValue, logger);
    return new Baggage(keyValues, logger);
  }

  private static Map<String, String> extractKeyValuesFromBaggageString(
      final @Nullable String headerValue, final @NotNull ILogger logger) {
    final @NotNull Map<String, String> keyValues = new HashMap<>();

    if (headerValue != null) {
      try {
        // see https://errorprone.info/bugpattern/StringSplitter for why limit is passed
        final String[] keyValueStrings = headerValue.split(",", -1);
        for (final String keyValueString : keyValueStrings) {
          try {
            final String[] keyAndValue = keyValueString.split("=", -1);
            if (keyAndValue.length == 2) {
              final String key = keyAndValue[0].trim();
              final String keyDecoded = decode(key);
              final String value = keyAndValue[1].trim();
              final String valueDecoded = decode(value);

              keyValues.put(keyDecoded, valueDecoded);
            } else {
              logger.log(
                  SentryLevel.ERROR, "Unable to decode baggage key value pair %s", keyValueString);
            }
          } catch (Throwable e) {
            logger.log(
                SentryLevel.ERROR, e, "Unable to decode baggage key value pair %s", keyValueString);
          }
        }
      } catch (Throwable e) {
        logger.log(SentryLevel.ERROR, e, "Unable to decode baggage header %s", headerValue);
      }
    }
    return keyValues;
  }

  public Baggage(final @NotNull ILogger logger) {
    this(new HashMap<>(), logger);
  }

  public Baggage(final @NotNull Map<String, String> keyValues, final @NotNull ILogger logger) {
    this.keyValues = keyValues;
    this.logger = logger;
  }

  public @NotNull String toHeaderString() {
    final StringBuilder sb = new StringBuilder();
    String separator = "";
    int listMemberCount = 0;

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

  public @Nullable String get(final @Nullable String key) {
    if (key == null) {
      return null;
    }

    return keyValues.get(key);
  }

  public void setTraceId(final @Nullable String traceId) {
    set("sentry-trace_id", traceId);
  }

  public void setPublicKey(final @Nullable String publicKey) {
    set("sentry-public_key", publicKey);
  }

  public void setEnvironment(final @Nullable String environment) {
    set("sentry-environment", environment);
  }

  public void setRelease(final @Nullable String release) {
    set("sentry-release", release);
  }

  public void setUserId(final @Nullable String userId) {
    set("sentry-user_id", userId);
  }

  public void setUserSegment(final @Nullable String userSegment) {
    set("sentry-user_segment", userSegment);
  }

  public void setTransaction(final @Nullable String transaction) {
    set("sentry-transaction", transaction);
  }

  public void setSampleRate(final @Nullable String sampleRate) {
    set("sentry-sample_rate", sampleRate);
  }

  public void set(final @NotNull String key, final @Nullable String value) {
    this.keyValues.put(key, value);
  }
}
