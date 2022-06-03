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
  private @NotNull SentryId traceId;
  private @NotNull String publicKey;
  private @Nullable String release;
  private @Nullable String environment;
  private @Nullable TraceContext.TraceContextUser user;
  private @Nullable String transaction;

  @SuppressWarnings("unused")
  private @Nullable Map<String, @NotNull Object> unknown;

  TraceContext(@NotNull SentryId traceId, @NotNull String publicKey) {
    this(traceId, publicKey, null, null, null, null);
  }

  TraceContext(
      @NotNull SentryId traceId,
      @NotNull String publicKey,
      @Nullable String release,
      @Nullable String environment,
      @Nullable TraceContext.TraceContextUser user,
      @Nullable String transaction) {
    this.traceId = traceId;
    this.publicKey = publicKey;
    this.release = release;
    this.environment = environment;
    this.user = user;
    this.transaction = transaction;
  }

  TraceContext(
      final @NotNull ITransaction transaction,
      final @Nullable User user,
      final @NotNull SentryOptions sentryOptions) {
    this(
        transaction.getSpanContext().getTraceId(),
        new Dsn(sentryOptions.getDsn()).getPublicKey(),
        sentryOptions.getRelease(),
        sentryOptions.getEnvironment(),
        user != null ? new TraceContextUser(user) : null,
        transaction.getName());
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

  public @Nullable TraceContextUser getUser() {
    return user;
  }

  public @Nullable String getTransaction() {
    return transaction;
  }

  public @NotNull Baggage toBaggage(@NotNull ILogger logger) {
    Baggage baggage = new Baggage(logger);

    baggage.setTraceId(traceId.toString());
    baggage.setPublicKey(publicKey);
    baggage.setRelease(release);
    baggage.setEnvironment(environment);
    baggage.setTransaction(transaction);

    final TraceContextUser user = this.user;
    if (user != null) {
      baggage.setUserId(user.id);
      baggage.setUserSegment(user.segment);
    }

    return baggage;
  }

  public static final class TraceContextUser implements JsonUnknown, JsonSerializable {
    private @Nullable String id;
    private @Nullable String segment;

    @SuppressWarnings("unused")
    private @Nullable Map<String, @NotNull Object> unknown;

    TraceContextUser(final @Nullable String id, final @Nullable String segment) {
      this.id = id;
      this.segment = segment;
    }

    public TraceContextUser(final @Nullable User protocolUser) {
      if (protocolUser != null) {
        this.id = protocolUser.getId();
        this.segment = getSegment(protocolUser);
      }
    }

    private static @Nullable String getSegment(final @NotNull User user) {
      final Map<String, String> others = user.getOthers();
      if (others != null) {
        return others.get("segment");
      } else {
        return null;
      }
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

    @Override
    public void serialize(@NotNull JsonObjectWriter writer, @NotNull ILogger logger)
        throws IOException {
      writer.beginObject();
      if (id != null) {
        writer.name(TraceContextUser.JsonKeys.ID).value(id);
      }
      if (segment != null) {
        writer.name(TraceContextUser.JsonKeys.SEGMENT).value(segment);
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
    public static final String TRANSACTION = "transaction";
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
    if (user != null) {
      if (user.id != null || user.segment != null || user.unknown != null) {
        writer.name(TraceContext.JsonKeys.USER).value(logger, user);
      }
    }
    if (transaction != null) {
      writer.name(TraceContext.JsonKeys.TRANSACTION).value(transaction);
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
      String transaction = null;

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
          case TraceContext.JsonKeys.TRANSACTION:
            transaction = reader.nextStringOrNull();
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
      TraceContext traceStateUser =
          new TraceContext(traceId, publicKey, release, environment, user, transaction);
      traceStateUser.setUnknown(unknown);
      reader.endObject();
      return traceStateUser;
    }

    private Exception missingRequiredFieldException(String field, ILogger logger) {
      String message = "Missing required field \"" + field + "\"";
      Exception exception = new IllegalStateException(message);
      logger.log(SentryLevel.ERROR, message, exception);
      return exception;
    }
  }
}
