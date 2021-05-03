package io.sentry;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.sentry.util.Objects;
import io.sentry.util.StringUtils;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SessionAdapter extends TypeAdapter<Session> {

  private final @NotNull SentryOptions options;

  public SessionAdapter(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "The SentryOptions is required.");
  }

  @Override
  public void write(JsonWriter writer, Session value) throws IOException {
    if (value == null) {
      writer.nullValue();
      return;
    }
    writer.beginObject();

    if (value.getSessionId() != null) {
      writer.name("sid").value(value.getSessionId().toString());
    }

    if (value.getDistinctId() != null) {
      writer.name("did").value(value.getDistinctId());
    }

    if (value.getInit() != null) {
      writer.name("init").value(value.getInit());
    }

    final Date started = value.getStarted();
    if (started != null) {
      writer.name("started").value(DateUtils.getTimestamp(started));
    }

    final Session.State status = value.getStatus();
    if (status != null) {
      writer.name("status").value(status.name().toLowerCase(Locale.ROOT));
    }

    if (value.getSequence() != null) {
      writer.name("seq").value(value.getSequence());
    }

    int errorCount = value.errorCount();
    if (errorCount > 0) {
      writer.name("errors").value(errorCount);
    }

    if (value.getDuration() != null) {
      writer.name("duration").value(value.getDuration());
    }

    if (value.getTimestamp() != null) {
      writer.name("timestamp").value(DateUtils.getTimestamp(value.getTimestamp()));
    }

    boolean hasInitAttrs = false;
    hasInitAttrs = initAttrs(writer, hasInitAttrs);

    if (value.getRelease() != null) {
      writer.name("release").value(value.getRelease());
    }

    if (value.getEnvironment() != null) {
      hasInitAttrs = initAttrs(writer, hasInitAttrs);

      writer.name("environment").value(value.getEnvironment());
    }

    if (value.getIpAddress() != null) {
      hasInitAttrs = initAttrs(writer, hasInitAttrs);

      writer.name("ip_address").value(value.getIpAddress());
    }

    if (value.getUserAgent() != null) {
      hasInitAttrs = initAttrs(writer, hasInitAttrs);

      writer.name("user_agent").value(value.getUserAgent());
    }

    if (hasInitAttrs) {
      writer.endObject();
    }

    writer.endObject();
  }

  private boolean initAttrs(JsonWriter writer, boolean hasInitAtts) throws IOException {
    if (!hasInitAtts) {
      writer.name("attrs").beginObject();
    }
    return true;
  }

  @Override
  public @Nullable Session read(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }
    UUID sid = null;
    String did = null;
    Boolean init = null;
    Date started = null;
    Session.State status = null;
    int errors = 0;
    Long seq = null;
    Double duration = null;
    Date timestamp = null;
    String release = null;
    String environment = null;
    String ipAddress = null;
    String userAgent = null;

    reader.beginObject();

    while (reader.hasNext()) {
      switch (reader.nextName()) {
        case "sid":
          String sidStr = null;
          try {
            sidStr = reader.nextString();
            sid = UUID.fromString(sidStr);
          } catch (IllegalArgumentException e) {
            options.getLogger().log(SentryLevel.ERROR, "%s sid is not valid.", sidStr);
          }
          break;
        case "did":
          did = reader.nextString();
          break;
        case "init":
          init = reader.nextBoolean();
          break;
        case "started":
          started = converTimeStamp(reader.nextString(), "started");
          break;
        case "status":
          String statusStr = null;
          try {
            statusStr = StringUtils.capitalize(reader.nextString());
            if (statusStr != null) {
              status = Session.State.valueOf(statusStr);
            }
          } catch (IllegalArgumentException e) {
            options.getLogger().log(SentryLevel.ERROR, "%s status is not valid.", statusStr);
          }
          break;
        case "errors":
          errors = reader.nextInt();
          break;
        case "seq":
          seq = reader.nextLong();
          break;
        case "duration":
          duration = reader.nextDouble();
          break;
        case "timestamp":
          timestamp = converTimeStamp(reader.nextString(), "timestamp");
          break;
        case "attrs":
          {
            reader.beginObject();

            while (reader.hasNext()) {
              switch (reader.nextName()) {
                case "release":
                  release = reader.nextString();
                  break;
                case "environment":
                  environment = reader.nextString();
                  break;
                case "ip_address":
                  ipAddress = reader.nextString();
                  break;
                case "user_agent":
                  userAgent = reader.nextString();
                  break;
                default:
                  reader.skipValue();
                  break;
              }
            }
            reader.endObject();
            break;
          }
        default:
          reader.skipValue();
          break;
      }
    }
    reader.endObject();

    if (status == null || started == null || release == null || release.isEmpty()) {
      options
          .getLogger()
          .log(SentryLevel.ERROR, "Session is gonna be dropped due to invalid fields.");
      return null;
    }

    return new Session(
        status,
        started,
        timestamp,
        errors,
        did,
        sid,
        init,
        seq,
        duration,
        ipAddress,
        userAgent,
        environment,
        release);
  }

  private @Nullable Date converTimeStamp(
      final @NotNull String timestamp, final @NotNull String field) {
    try {
      return DateUtils.getDateTime(timestamp);
    } catch (IllegalArgumentException e) {
      options.getLogger().log(SentryLevel.ERROR, e, "Error converting session (%s) field.", field);
    }
    return null;
  }
}
