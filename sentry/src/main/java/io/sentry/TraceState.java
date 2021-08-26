package io.sentry;

import io.sentry.protocol.SentryId;
import io.sentry.protocol.User;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TraceState {
  private @NotNull SentryId traceId;
  private @NotNull String publicKey;
  private @Nullable String release;
  private @Nullable String environment;
  private @Nullable TraceState.TraceStateUser user;
  private @Nullable String transaction;

  TraceState(@NotNull SentryId traceId, @NotNull String publicKey) {
    this(traceId, publicKey, null, null, null, null);
  }

  TraceState(
      @NotNull SentryId traceId,
      @NotNull String publicKey,
      @Nullable String release,
      @Nullable String environment,
      @Nullable TraceState.TraceStateUser user,
      @Nullable String transaction) {
    this.traceId = traceId;
    this.publicKey = publicKey;
    this.release = release;
    this.environment = environment;
    this.user = user;
    this.transaction = transaction;
  }

  TraceState(
      final @NotNull ITransaction transaction,
      final @Nullable User user,
      final @NotNull SentryOptions sentryOptions) {
    this(
        transaction.getSpanContext().getTraceId(),
        new Dsn(sentryOptions.getDsn()).getPublicKey(),
        sentryOptions.getRelease(),
        sentryOptions.getEnvironment(),
        user != null ? new TraceStateUser(user) : null,
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

  public @Nullable TraceStateUser getUser() {
    return user;
  }

  public @Nullable String getTransaction() {
    return transaction;
  }

  static final class TraceStateUser {
    private @Nullable String id;
    private @Nullable String segment;

    TraceStateUser(final @Nullable User protocolUser) {
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
  }
}
