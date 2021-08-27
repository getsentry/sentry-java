package io.sentry;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.SentryId;
import io.sentry.protocol.SentryPackage;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryEnvelopeHeaderAdapter extends TypeAdapter<SentryEnvelopeHeader> {

  @Override
  public void write(final @NotNull JsonWriter writer, final @Nullable SentryEnvelopeHeader value)
      throws IOException {
    if (value == null) {
      writer.nullValue();
      return;
    }
    writer.beginObject();

    if (value.getEventId() != null) {
      writer.name("event_id");
      writer.value(value.getEventId().toString());
    }

    final SdkVersion sdkVersion = value.getSdkVersion();
    if (sdkVersion != null) {
      if (hasValidSdkVersion(sdkVersion)) {
        writer.name("sdk").beginObject();

        writer.name("name").value(sdkVersion.getName());
        writer.name("version").value(sdkVersion.getVersion());

        final List<String> integrations = sdkVersion.getIntegrations();
        if (integrations != null) {
          writer.name("integrations").beginArray();

          for (final String integration : integrations) {
            writer.value(integration);
          }

          // integrations
          writer.endArray();
        }

        final List<SentryPackage> packages = sdkVersion.getPackages();
        if (packages != null) {
          writer.name("packages").beginArray();

          for (final SentryPackage item : packages) {
            // item packages
            writer.beginObject();

            writer.name("name").value(item.getName());
            writer.name("version").value(item.getVersion());

            // item packages
            writer.endObject();
          }

          // packages
          writer.endArray();
        }

        // sdk
        writer.endObject();
      }
    }
    TraceState trace = value.getTrace();
    if (trace != null) {
      writer.name("trace");
      writer.beginObject();
      writer.name("trace_id").value(trace.getTraceId().toString());
      writer.name("public_key").value(trace.getPublicKey());
      if (trace.getRelease() != null) {
        writer.name("release").value(trace.getRelease());
      }
      if (trace.getEnvironment() != null) {
        writer.name("environment").value(trace.getEnvironment());
      }
      if (trace.getTransaction() != null) {
        writer.name("transaction").value(trace.getTransaction());
      }
      if (trace.getUser() != null) {
        writer.name("user");
        writer.beginObject();
        if (trace.getUser().getId() != null) {
          writer.name("id").value(trace.getUser().getId());
        }
        if (trace.getUser().getSegment() != null) {
          writer.name("segment").value(trace.getUser().getSegment());
        }
        writer.endObject();
      }
      writer.endObject();
    }

    writer.endObject();
  }

  /**
   * Returns if SdkVersion is a valid object, with non empty name and version
   *
   * @param sdkVersion the SdkVersion object
   * @return true if contains a valid name and version or false otherwise
   */
  private boolean hasValidSdkVersion(final @NotNull SdkVersion sdkVersion) {
    return sdkVersion.getName() != null
        && !sdkVersion.getName().isEmpty()
        && sdkVersion.getVersion() != null
        && !sdkVersion.getVersion().isEmpty();
  }

  @SuppressWarnings("deprecation")
  @Override
  public @Nullable SentryEnvelopeHeader read(final @NotNull JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }

    SentryId eventId = null;
    SdkVersion sdkVersion = null;
    TraceState traceState = null;

    reader.beginObject();
    while (reader.hasNext()) {
      switch (reader.nextName()) {
        case "event_id":
          eventId = new SentryId(reader.nextString());
          break;
        case "sdk":
          {
            reader.beginObject();
            sdkVersion = new SdkVersion();

            while (reader.hasNext()) {
              switch (reader.nextName()) {
                case "name":
                  sdkVersion.setName(reader.nextString());
                  break;
                case "version":
                  sdkVersion.setVersion(reader.nextString());
                  break;
                case "integrations":
                  reader.beginArray();

                  while (reader.hasNext()) {
                    final String integration = reader.nextString();
                    if (integration != null) {
                      sdkVersion.addIntegration(integration);
                    }
                  }
                  reader.endArray();
                  break;
                case "packages":
                  // packages
                  reader.beginArray();

                  while (reader.hasNext()) {
                    // packages item
                    reader.beginObject();

                    String name = null;
                    String version = null;
                    while (reader.hasNext()) {
                      switch (reader.nextName()) {
                        case "name":
                          name = reader.nextString();
                          break;
                        case "version":
                          version = reader.nextString();
                          break;
                        default:
                          reader.skipValue();
                      }
                    }
                    // packages should not contain null names or versions
                    if (name != null && version != null) {
                      sdkVersion.addPackage(name, version);
                    }

                    // packages item
                    reader.endObject();
                  }

                  // packages
                  reader.endArray();
                  break;
                default:
                  reader.skipValue();
                  break;
              }
            }

            // we don't read the unknown fields for the envelope header, we skip them

            reader.endObject();
            break;
          }
        case "trace":
          {
            reader.beginObject();
            SentryId traceId = null;
            String publicKey = null;
            String release = null;
            String environment = null;
            String transaction = null;
            String userId = null;
            String segment = null;
            while (reader.hasNext()) {
              switch (reader.nextName()) {
                case "trace_id":
                  traceId = new SentryId(reader.nextString());
                  break;
                case "public_key":
                  publicKey = reader.nextString();
                  break;
                case "release":
                  release = reader.nextString();
                  break;
                case "environment":
                  environment = reader.nextString();
                  break;
                case "transaction":
                  transaction = reader.nextString();
                  break;
                case "user":
                  reader.beginObject();
                  while (reader.hasNext()) {
                    switch (reader.nextName()) {
                      case "id":
                        userId = reader.nextString();
                        break;
                      case "segment":
                        segment = reader.nextString();
                        break;
                      default:
                        reader.skipValue();
                    }
                  }
                  reader.endObject();
                  break;
                default:
                  reader.skipValue();
                  break;
              }
            }
            if (traceId != null && publicKey != null) {
              traceState =
                  new TraceState(
                      traceId,
                      publicKey,
                      release,
                      environment,
                      new TraceState.TraceStateUser(userId, segment),
                      transaction);
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

    return new SentryEnvelopeHeader(eventId, sdkVersion, traceState);
  }
}
