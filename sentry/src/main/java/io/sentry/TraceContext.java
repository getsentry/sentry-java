package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.util.SampleRateUtil;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class TraceContext implements JsonUnknown, JsonSerializable {
  private @NotNull SentryId traceId;
  private @NotNull String publicKey;
  private @Nullable String release;
  private @Nullable String environment;
  private @Nullable String userId;
  private @Nullable String userSegment;
  private @Nullable String transaction;
  private @Nullable String sampleRate;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  TraceContext(@NotNull SentryId traceId, @NotNull String publicKey) {
    this(traceId, publicKey, null, null, null, null, null, null);
  }

  TraceContext(
      @NotNull SentryId traceId,
      @NotNull String publicKey,
      @Nullable String release,
      @Nullable String environment,
      @Nullable String userId,
      @Nullable String userSegment,
      @Nullable String transaction,
      @Nullable String sampleRate) {
    this.traceId = traceId;
    this.publicKey = publicKey;
    this.release = release;
    this.environment = environment;
    this.userId = userId;
    this.userSegment = userSegment;
    this.transaction = transaction;
    this.sampleRate = sampleRate;
  }

  TraceContext(
      final @NotNull ITransaction transaction,
      final @Nullable User user,
      final @NotNull SentryOptions sentryOptions,
      final @Nullable TracesSamplingDecision samplingDecision) {
    this(
        transaction.getSpanContext().getTraceId(),
        new Dsn(sentryOptions.getDsn()).getPublicKey(),
        sentryOptions.getRelease(),
        sentryOptions.getEnvironment(),
        getUserId(sentryOptions, user),
        user != null ? getSegment(user) : null,
        transaction.getName(),
        sampleRateToString(sampleRate(samplingDecision)));
  }

  private static @Nullable String getUserId(
      final @NotNull SentryOptions options, final @Nullable User user) {
    if (options.isSendDefaultPii() && user != null) {
      return user.getId();
    }

    return null;
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

  public @NotNull SentryId getTraceId() {
    return traceId;
  }

  public @NotNull String getPublicKey() {
    return publicKey;
  }

  public @Nullable String getRelease() {
    return release;
  }

  public @Nullable String getEnvironment() {
    return environment;
  }

  public @Nullable String getUserId() {
    return userId;
  }

  public @Nullable String getUserSegment() {
    return userSegment;
  }

  public @Nullable String getTransaction() {
    return transaction;
  }

  public @Nullable String getSampleRate() {
    return sampleRate;
  }

  public @NotNull Baggage toBaggage(@NotNull ILogger logger) {
    Baggage baggage = new Baggage(logger);

    baggage.setTraceId(traceId.toString());
    baggage.setPublicKey(publicKey);
    baggage.setSampleRate(sampleRate);
    baggage.setRelease(release);
    baggage.setEnvironment(environment);
    baggage.setTransaction(transaction);
    baggage.setUserId(userId);
    baggage.setUserSegment(userSegment);

    return baggage;
  }

  /** @deprecated only here to support parsing legacy JSON with non flattened user */
  @Deprecated
  private static final class TraceContextUser implements JsonUnknown {
    private @Nullable String id;
    private @Nullable String segment;

    @SuppressWarnings("unused")
    private @Nullable Map<String, @NotNull Object> unknown;

    private TraceContextUser(final @Nullable String id, final @Nullable String segment) {
      this.id = id;
      this.segment = segment;
    }

    public @Nullable String getId() {
      return id;
    }

    public @Nullable String getSegment() {
      return segment;
    }

    // region json

    @Nullable
    @Override
    public Map<String, Object> getUnknown() {
      return unknown;
    }

    @Override
    public void setUnknown(@Nullable Map<String, Object> unknown) {
      this.unknown = unknown;
    }

    public static final class JsonKeys {
      public static final String ID = "id";
      public static final String SEGMENT = "segment";
    }

    public static final class Deserializer implements JsonDeserializer<TraceContextUser> {
      @Override
      public @NotNull TraceContextUser deserialize(
          @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
        reader.beginObject();

        String id = null;
        String segment = null;
        Map<String, Object> unknown = null;
        while (reader.peek() == JsonToken.NAME) {
          final String nextName = reader.nextName();
          switch (nextName) {
            case TraceContextUser.JsonKeys.ID:
              id = reader.nextStringOrNull();
              break;
            case TraceContextUser.JsonKeys.SEGMENT:
              segment = reader.nextStringOrNull();
              break;
            default:
              if (unknown == null) {
                unknown = new ConcurrentHashMap<>();
              }
              reader.nextUnknown(logger, unknown, nextName);
              break;
          }
        }
        TraceContextUser traceStateUser = new TraceContextUser(id, segment);
        traceStateUser.setUnknown(unknown);
        reader.endObject();
        return traceStateUser;
      }
    }

    // endregion
  }

  // region json

  @Nullable
  @Override
  public Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public void setUnknown(@Nullable Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  public static final class JsonKeys {
    public static final String TRACE_ID = "trace_id";
    public static final String PUBLIC_KEY = "public_key";
    public static final String RELEASE = "release";
    public static final String ENVIRONMENT = "environment";
    public static final String USER = "user";
    public static final String USER_ID = "user_id";
    public static final String USER_SEGMENT = "user_segment";
    public static final String TRANSACTION = "transaction";
    public static final String SAMPLE_RATE = "sample_rate";
  }

  @Override
  public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    writer.name(TraceContext.JsonKeys.TRACE_ID).value(logger, traceId);
    writer.name(TraceContext.JsonKeys.PUBLIC_KEY).value(publicKey);
    if (release != null) {
      writer.name(TraceContext.JsonKeys.RELEASE).value(release);
    }
    if (environment != null) {
      writer.name(TraceContext.JsonKeys.ENVIRONMENT).value(environment);
    }
    if (userId != null) {
      writer.name(TraceContext.JsonKeys.USER_ID).value(userId);
    }
    if (userSegment != null) {
      writer.name(TraceContext.JsonKeys.USER_SEGMENT).value(userSegment);
    }
    if (transaction != null) {
      writer.name(TraceContext.JsonKeys.TRANSACTION).value(transaction);
    }
    if (sampleRate != null) {
      writer.name(TraceContext.JsonKeys.SAMPLE_RATE).value(sampleRate);
    }
    if (unknown != null) {
      for (String key : unknown.keySet()) {
        Object value = unknown.get(key);
        writer.name(key);
        writer.value(logger, value);
      }
    }
    writer.endObject();
  }

  public static final class Deserializer implements JsonDeserializer<TraceContext> {
    @Override
    public @NotNull TraceContext deserialize(
        @NotNull JsonObjectReader reader, @NotNull ILogger logger) throws Exception {
      reader.beginObject();

      SentryId traceId = null;
      String publicKey = null;
      String release = null;
      String environment = null;
      TraceContextUser user = null;
      String userId = null;
      String userSegment = null;
      String transaction = null;
      String sampleRate = null;

      Map<String, Object> unknown = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case TraceContext.JsonKeys.TRACE_ID:
            traceId = new SentryId.Deserializer().deserialize(reader, logger);
            break;
          case TraceContext.JsonKeys.PUBLIC_KEY:
            publicKey = reader.nextString();
            break;
          case TraceContext.JsonKeys.RELEASE:
            release = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.ENVIRONMENT:
            environment = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.USER:
            user = reader.nextOrNull(logger, new TraceContextUser.Deserializer());
            break;
          case TraceContext.JsonKeys.USER_ID:
            userId = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.USER_SEGMENT:
            userSegment = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.TRANSACTION:
            transaction = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.SAMPLE_RATE:
            sampleRate = reader.nextStringOrNull();
            break;
          default:
            if (unknown == null) {
              unknown = new ConcurrentHashMap<>();
            }
            reader.nextUnknown(logger, unknown, nextName);
            break;
        }
      }
      if (traceId == null) {
        throw missingRequiredFieldException(TraceContext.JsonKeys.TRACE_ID, logger);
      }
      if (publicKey == null) {
        throw missingRequiredFieldException(TraceContext.JsonKeys.PUBLIC_KEY, logger);
      }
      if (user != null) {
        if (userId == null) {
          userId = user.getId();
        }
        if (userSegment == null) {
          userSegment = user.getSegment();
        }
      }
      TraceContext traceContext =
          new TraceContext(
              traceId,
              publicKey,
              release,
              environment,
              userId,
              userSegment,
              transaction,
              sampleRate);
      traceContext.setUnknown(unknown);
      reader.endObject();
      return traceContext;
    }

    private Exception missingRequiredFieldException(String field, ILogger logger) {
      String message = "Missing required field \"" + field + "\"";
      Exception exception = new IllegalStateException(message);
      logger.log(SentryLevel.ERROR, message, exception);
      return exception;
    }
  }
}
