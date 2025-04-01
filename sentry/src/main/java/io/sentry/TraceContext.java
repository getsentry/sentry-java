package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public final class TraceContext implements JsonUnknown, JsonSerializable {
  private final @NotNull SentryId traceId;
  private final @NotNull String publicKey;
  private final @Nullable String release;
  private final @Nullable String environment;
  private final @Nullable String userId;
  private final @Nullable String transaction;
  private final @Nullable String sampleRate;
  private final @Nullable String sampleRand;
  private final @Nullable String sampled;
  private final @Nullable SentryId replayId;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  TraceContext(@NotNull SentryId traceId, @NotNull String publicKey) {
    this(traceId, publicKey, null, null, null, null, null, null, null);
  }

  @SuppressWarnings("InlineMeSuggester")
  /**
   * @deprecated please use the constructor than also takes sampleRand
   */
  @Deprecated
  TraceContext(
      @NotNull SentryId traceId,
      @NotNull String publicKey,
      @Nullable String release,
      @Nullable String environment,
      @Nullable String userId,
      @Nullable String transaction,
      @Nullable String sampleRate,
      @Nullable String sampled,
      @Nullable SentryId replayId) {
    this(
        traceId,
        publicKey,
        release,
        environment,
        userId,
        transaction,
        sampleRate,
        sampled,
        replayId,
        null);
  }

  TraceContext(
      @NotNull SentryId traceId,
      @NotNull String publicKey,
      @Nullable String release,
      @Nullable String environment,
      @Nullable String userId,
      @Nullable String transaction,
      @Nullable String sampleRate,
      @Nullable String sampled,
      @Nullable SentryId replayId,
      @Nullable String sampleRand) {
    this.traceId = traceId;
    this.publicKey = publicKey;
    this.release = release;
    this.environment = environment;
    this.userId = userId;
    this.transaction = transaction;
    this.sampleRate = sampleRate;
    this.sampled = sampled;
    this.replayId = replayId;
    this.sampleRand = sampleRand;
  }

  @SuppressWarnings("UnusedMethod")
  private static @Nullable String getUserId(
      final @NotNull SentryOptions options, final @Nullable User user) {
    if (options.isSendDefaultPii() && user != null) {
      return user.getId();
    }

    return null;
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

  public @Nullable String getTransaction() {
    return transaction;
  }

  public @Nullable String getSampleRate() {
    return sampleRate;
  }

  public @Nullable String getSampleRand() {
    return sampleRand;
  }

  public @Nullable String getSampled() {
    return sampled;
  }

  public @Nullable SentryId getReplayId() {
    return replayId;
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
    public static final String USER_ID = "user_id";
    public static final String TRANSACTION = "transaction";
    public static final String SAMPLE_RATE = "sample_rate";
    public static final String SAMPLE_RAND = "sample_rand";
    public static final String SAMPLED = "sampled";
    public static final String REPLAY_ID = "replay_id";
  }

  @Override
  public void serialize(final @NotNull ObjectWriter writer, final @NotNull ILogger logger)
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
    if (transaction != null) {
      writer.name(TraceContext.JsonKeys.TRANSACTION).value(transaction);
    }
    if (sampleRate != null) {
      writer.name(TraceContext.JsonKeys.SAMPLE_RATE).value(sampleRate);
    }
    if (sampleRand != null) {
      writer.name(TraceContext.JsonKeys.SAMPLE_RAND).value(sampleRand);
    }
    if (sampled != null) {
      writer.name(TraceContext.JsonKeys.SAMPLED).value(sampled);
    }
    if (replayId != null) {
      writer.name(TraceContext.JsonKeys.REPLAY_ID).value(logger, replayId);
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
    public @NotNull TraceContext deserialize(@NotNull ObjectReader reader, @NotNull ILogger logger)
        throws Exception {
      reader.beginObject();

      SentryId traceId = null;
      String publicKey = null;
      String release = null;
      String environment = null;
      String userId = null;
      String transaction = null;
      String sampleRate = null;
      String sampleRand = null;
      String sampled = null;
      SentryId replayId = null;

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
          case TraceContext.JsonKeys.USER_ID:
            userId = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.TRANSACTION:
            transaction = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.SAMPLE_RATE:
            sampleRate = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.SAMPLE_RAND:
            sampleRand = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.SAMPLED:
            sampled = reader.nextStringOrNull();
            break;
          case TraceContext.JsonKeys.REPLAY_ID:
            replayId = new SentryId.Deserializer().deserialize(reader, logger);
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
      TraceContext traceContext =
          new TraceContext(
              traceId,
              publicKey,
              release,
              environment,
              userId,
              transaction,
              sampleRate,
              sampled,
              replayId,
              sampleRand);
      traceContext.setUnknown(unknown);
      reader.endObject();
      return traceContext;
    }

    private Exception missingRequiredFieldException(String field, ILogger logger) {
      String message = "Missing required field \"" + field + "\"";
      Exception exception = new IllegalStateException(message);
      if (logger.isEnabled(SentryLevel.ERROR)) {
        logger.log(SentryLevel.ERROR, message, exception);
      }
      return exception;
    }
  }
}
