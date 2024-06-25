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
  private final @Nullable String userSegment;
  private final @Nullable String transaction;
  private final @Nullable String sampleRate;
  private final @Nullable String sampled;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  TraceContext(@NotNull SentryId traceId, @NotNull String publicKey) {
    this(traceId, publicKey, null, null, null, null, null, null, null);
  }

  TraceContext(
      @NotNull SentryId traceId,
      @NotNull String publicKey,
      @Nullable String release,
      @Nullable String environment,
      @Nullable String userId,
      @Nullable String userSegment,
      @Nullable String transaction,
      @Nullable String sampleRate,
      @Nullable String sampled) {
    this.traceId = traceId;
    this.publicKey = publicKey;
    this.release = release;
    this.environment = environment;
    this.userId = userId;
    this.userSegment = userSegment;
    this.transaction = transaction;
    this.sampleRate = sampleRate;
    this.sampled = sampled;
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

  /**
   * @deprecated has no effect and will be removed in the next major update.
   */
  @Deprecated
  public @Nullable String getUserSegment() {
    return userSegment;
  }

  public @Nullable String getTransaction() {
    return transaction;
  }

  public @Nullable String getSampleRate() {
    return sampleRate;
  }

  public @Nullable String getSampled() {
    return sampled;
  }

  /**
   * @deprecated only here to support parsing legacy JSON with non flattened user
   */
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

    /**
     * @deprecated has no effect and will be removed in the next major update.
     */
    @Deprecated
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
    public static final String SAMPLED = "sampled";
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
    if (userSegment != null) {
      writer.name(TraceContext.JsonKeys.USER_SEGMENT).value(userSegment);
    }
    if (transaction != null) {
      writer.name(TraceContext.JsonKeys.TRANSACTION).value(transaction);
    }
    if (sampleRate != null) {
      writer.name(TraceContext.JsonKeys.SAMPLE_RATE).value(sampleRate);
    }
    if (sampled != null) {
      writer.name(TraceContext.JsonKeys.SAMPLED).value(sampled);
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
      String sampled = null;

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
          case TraceContext.JsonKeys.SAMPLED:
            sampled = reader.nextStringOrNull();
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
              sampleRate,
              sampled);
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
