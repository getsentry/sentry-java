package io.sentry.protocol;

import io.sentry.ILogger;
import io.sentry.JsonDeserializer;
import io.sentry.JsonObjectReader;
import io.sentry.JsonObjectWriter;
import io.sentry.JsonSerializable;
import io.sentry.SentryLevel;
import io.sentry.SessionAggregates;
import io.sentry.util.Objects;
import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Container for server-side aggregated sessions. */
public final class Sessions implements JsonSerializable {
  private final @NotNull List<Aggregate> aggregates = new ArrayList<>();
  private final @NotNull Attributes attrs;

  public Sessions(final @NotNull SessionAggregates sessionAggregates) {
    Objects.requireNonNull(sessionAggregates, "sessionAggregates is required");
    this.attrs =
        new Attributes(
            sessionAggregates.getAttributes().getRelease(),
            sessionAggregates.getAttributes().getEnvironment());
    for (final Map.Entry<String, SessionAggregates.SessionStats> entry :
        sessionAggregates.getAggregates().entrySet()) {
      aggregates.add(
          new Aggregate(
              entry.getKey(),
              entry.getValue().getExited().get(),
              entry.getValue().getErrored().get(),
              entry.getValue().getCrashed().get()));
    }
  }

  Sessions(final @NotNull List<Aggregate> aggregates, final @NotNull Attributes attrs) {
    this.aggregates.addAll(aggregates);
    this.attrs = attrs;
  }

  public @NotNull List<Aggregate> getAggregates() {
    return aggregates;
  }

  public @NotNull Attributes getAttrs() {
    return attrs;
  }

  public static final class JsonKeys {
    public static final String AGGREGATES = "aggregates";
    public static final String ATTRS = "attrs";
  }

  public static final class Deserializer implements JsonDeserializer<Sessions> {
    @Override
    public @NotNull Sessions deserialize(
        final @NotNull JsonObjectReader reader, final @NotNull ILogger logger) throws Exception {
      reader.beginObject();
      List<Aggregate> aggregates = new ArrayList<>();
      Attributes attributes = null;
      while (reader.peek() == JsonToken.NAME) {
        final String nextName = reader.nextName();
        switch (nextName) {
          case Sessions.JsonKeys.AGGREGATES:
            final List<Aggregate> list = reader.nextList(logger, new Aggregate.Deserializer());
            if (list != null) {
              aggregates = list;
            }
            break;
          case Sessions.JsonKeys.ATTRS:
            attributes = new Attributes.Deserializer().deserialize(reader, logger);
            break;
        }
      }

      if (attributes == null) {
        throw missingRequiredFieldException(Sessions.JsonKeys.ATTRS, logger);
      }

      reader.endObject();
      return new Sessions(aggregates, attributes);
    }
  }

  @Override
  public void serialize(final @NotNull JsonObjectWriter writer, final @NotNull ILogger logger)
      throws IOException {
    writer.beginObject();
    if (!aggregates.isEmpty()) {
      writer.name(Sessions.JsonKeys.AGGREGATES).value(logger, aggregates);
    }
    writer.name(Sessions.JsonKeys.ATTRS).value(logger, attrs);
    writer.endObject();
  }

  public static final class Attributes implements JsonSerializable {
    private final @NotNull String release;
    private final @Nullable String environment;

    public Attributes(final @NotNull String release, final @Nullable String environment) {
      this.release = release;
      this.environment = environment;
    }

    public @NotNull String getRelease() {
      return release;
    }

    public @Nullable String getEnvironment() {
      return environment;
    }

    public static final class JsonKeys {
      public static final String RELEASE = "release";
      public static final String ENVIRONMENT = "environment";
    }

    @Override
    public void serialize(final @NotNull JsonObjectWriter writer, final @NotNull ILogger logger)
        throws IOException {
      writer.beginObject();
      writer.name(Attributes.JsonKeys.RELEASE).value(release);
      if (environment != null) {
        writer.name(Attributes.JsonKeys.ENVIRONMENT).value(environment);
      }
      writer.endObject();
    }

    public static final class Deserializer implements JsonDeserializer<Attributes> {

      @Override
      public @NotNull Attributes deserialize(
          final @NotNull JsonObjectReader reader, final @NotNull ILogger logger) throws Exception {
        reader.beginObject();
        String release = "";
        String environment = null;
        while (reader.peek() == JsonToken.NAME) {
          final String nextName = reader.nextName();
          switch (nextName) {
            case Attributes.JsonKeys.RELEASE:
              release = reader.nextString();
              break;
            case Attributes.JsonKeys.ENVIRONMENT:
              environment = reader.nextString();
              break;
          }
        }
        reader.endObject();
        return new Attributes(release, environment);
      }
    }
  }

  public static final class Aggregate implements JsonSerializable {
    private final @NotNull String started;
    private final long exited;
    private final long errored;
    private final long crashed;

    public Aggregate(final @NotNull String started, long exited, long errored, long crashed) {
      this.started = started;
      this.exited = exited;
      this.errored = errored;
      this.crashed = crashed;
    }

    public @NotNull String getStarted() {
      return started;
    }

    public long getExited() {
      return exited;
    }

    public long getErrored() {
      return errored;
    }

    public long getCrashed() {
      return crashed;
    }

    @Override
    public void serialize(final @NotNull JsonObjectWriter writer, final @NotNull ILogger logger)
        throws IOException {
      writer.beginObject();
      writer.name(Aggregate.JsonKeys.STARTED).value(started);
      writer.name(Aggregate.JsonKeys.EXITED).value(exited);
      writer.name(Aggregate.JsonKeys.ERRORED).value(errored);
      writer.name(Aggregate.JsonKeys.CRASHED).value(crashed);
      writer.endObject();
    }

    public static final class JsonKeys {
      public static final String STARTED = "started";
      public static final String EXITED = "exited";
      public static final String ERRORED = "errored";
      public static final String CRASHED = "crashed";
    }

    public static final class Deserializer implements JsonDeserializer<Aggregate> {
      @Override
      public @NotNull Aggregate deserialize(
          final @NotNull JsonObjectReader reader, final @NotNull ILogger logger) throws Exception {
        reader.beginObject();
        String started = "";
        long exited = 0;
        long errored = 0;
        long crashed = 0;
        while (reader.peek() == JsonToken.NAME) {
          final String nextName = reader.nextName();
          switch (nextName) {
            case Aggregate.JsonKeys.STARTED:
              started = reader.nextString();
              break;
            case Aggregate.JsonKeys.EXITED:
              exited = reader.nextLong();
              break;
            case Aggregate.JsonKeys.ERRORED:
              errored = reader.nextLong();
              break;
            case Aggregate.JsonKeys.CRASHED:
              crashed = reader.nextLong();
              break;
          }
        }
        reader.endObject();
        return new Aggregate(started, exited, errored, crashed);
      }
    }
  }

  private static Exception missingRequiredFieldException(
      final @NotNull String field, final @NotNull ILogger logger) {
    String message = "Missing required field \"" + field + "\"";
    Exception exception = new IllegalStateException(message);
    logger.log(SentryLevel.ERROR, message, exception);
    return exception;
  }
}
