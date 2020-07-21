package io.sentry.core;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.sentry.core.protocol.SdkVersion;
import io.sentry.core.protocol.SentryId;
import io.sentry.core.protocol.SentryPackage;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryEnvelopeHeaderAdapter extends TypeAdapter<SentryEnvelopeHeader> {

  @Override
  public void write(JsonWriter writer, SentryEnvelopeHeader value) throws IOException {
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

  @Override
  public SentryEnvelopeHeader read(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }

    SentryId eventId = null;
    SdkVersion sdkVersion = null;

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
                    sdkVersion.addIntegration(reader.nextString());
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
                    sdkVersion.addPackage(name, version);

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
        default:
          reader.skipValue();
          break;
      }
    }
    reader.endObject();

    return new SentryEnvelopeHeader(eventId, sdkVersion);
  }
}
