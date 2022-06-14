package io.sentry;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

  static final @NotNull String CHARSET = StandardCharsets.UTF_8.toString();
  static final @NotNull Integer MAX_BAGGAGE_STRING_LENGTH = 8192;
  static final @NotNull Integer MAX_BAGGAGE_LIST_MEMBER_COUNT = 64;

  final @NotNull Map<String, String> keyValues;
  final @NotNull ILogger logger;

  public static Baggage fromHeader(@Nullable List<String> headerValues, @NotNull ILogger logger) {
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

  public static Baggage fromHeader(@Nullable String headerValue, @NotNull ILogger logger) {
    final Map<String, String> keyValues = extractKeyValuesFromBaggageString(headerValue, logger);
    return new Baggage(keyValues, logger);
  }

  private static Map<String, String> extractKeyValuesFromBaggageString(
      @Nullable String headerValue, @NotNull ILogger logger) {
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

  public Baggage(@NotNull ILogger logger) {
    this(new HashMap<>(), logger);
  }

  public Baggage(@NotNull Map<String, String> keyValues, @NotNull ILogger logger) {
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
                "Unable to encode baggage key value pair (key=%s,value=%s).",
                key,
                value);
          }
        }
      }
    }

    return sb.toString();
  }

  private String encode(String value) throws UnsupportedEncodingException {
    return URLEncoder.encode(value, CHARSET).replaceAll("\\+", "%20");
  }

  private static String decode(String value) throws UnsupportedEncodingException {
    return URLDecoder.decode(value, CHARSET);
  }

  public @Nullable String get(@Nullable String key) {
    if (key == null) {
      return null;
    }

    return keyValues.get(key);
  }

  public void setTraceId(@Nullable String traceId) {
    set("sentry-traceid", traceId);
  }

  public void setPublicKey(@Nullable String publicKey) {
    set("sentry-publickey", publicKey);
  }

  public void setEnvironment(@Nullable String environment) {
    set("sentry-environment", environment);
  }

  public void setRelease(@Nullable String release) {
    set("sentry-release", release);
  }

  public void setUserId(@Nullable String userId) {
    set("sentry-userid", userId);
  }

  public void setUserSegment(@Nullable String userSegment) {
    set("sentry-usersegment", userSegment);
  }

  public void setTransaction(@Nullable String transaction) {
    set("sentry-transaction", transaction);
  }

  public void set(@NotNull String key, @Nullable String value) {
    this.keyValues.put(key, value);
  }
}
