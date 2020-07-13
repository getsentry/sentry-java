package io.sentry.core;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import io.sentry.core.protocol.SdkInfo;
import io.sentry.core.protocol.SentryId;
import java.io.IOException;
import org.jetbrains.annotations.ApiStatus;

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

    final SdkInfo sdkInfo = value.getSdkInfo();
    if (sdkInfo != null) {
      boolean hasInitAttrs = false;

      if (sdkInfo.getSdkName() != null) {
        hasInitAttrs = initAttrs(writer, hasInitAttrs);
        writer.name("sdk_name").value(sdkInfo.getSdkName());

        // we only add versions if the name is not null
        if (sdkInfo.getVersionMajor() != null) {
          hasInitAttrs = initAttrs(writer, hasInitAttrs);
          writer.name("version_major").value(sdkInfo.getVersionMajor());
        }
        if (sdkInfo.getVersionMinor() != null) {
          hasInitAttrs = initAttrs(writer, hasInitAttrs);
          writer.name("version_minor").value(sdkInfo.getVersionMinor());
        }
        if (sdkInfo.getVersionPatchlevel() != null) {
          hasInitAttrs = initAttrs(writer, hasInitAttrs);
          writer.name("version_patchlevel").value(sdkInfo.getVersionPatchlevel());
        }
      }

      // we don't write the unknown fields for the envelope header, we skip them

      if (hasInitAttrs) {
        writer.endObject();
      }
    }

    writer.endObject();
  }

  private boolean initAttrs(JsonWriter writer, boolean hasInitAtts) throws IOException {
    if (!hasInitAtts) {
      writer.name("sdk_info").beginObject();
    }
    return true;
  }

  @Override
  public SentryEnvelopeHeader read(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }

    SentryId eventId = null;
    SdkInfo sdkInfo = null;

    reader.beginObject();
    while (reader.hasNext()) {
      switch (reader.nextName()) {
        case "event_id":
          eventId = new SentryId(reader.nextString());
          break;
        case "sdk_info":
          {
            reader.beginObject();
            sdkInfo = new SdkInfo();

            while (reader.hasNext()) {
              switch (reader.nextName()) {
                case "sdk_name":
                  sdkInfo.setSdkName(reader.nextString());
                  break;
                case "version_major":
                  sdkInfo.setVersionMajor(reader.nextInt());
                  break;
                case "version_minor":
                  sdkInfo.setVersionMinor(reader.nextInt());
                  break;
                case "version_patchlevel":
                  sdkInfo.setVersionPatchlevel(reader.nextInt());
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

    return new SentryEnvelopeHeader(eventId, sdkInfo);
  }
}
