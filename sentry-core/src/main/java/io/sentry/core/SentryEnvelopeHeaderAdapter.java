package io.sentry.core;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
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

    writer.endObject();
  }

  @Override
  public SentryEnvelopeHeader read(JsonReader reader) throws IOException {
    if (reader.peek() == JsonToken.NULL) {
      reader.nextNull();
      return null;
    }

    SentryId sentryId = SentryId.EMPTY_ID;

    reader.beginObject();
    while (reader.hasNext()) {
      switch (reader.nextName()) {
        case "event_id":
          sentryId = new SentryId(reader.nextString());
          break;
        default:
          reader.skipValue();
          break;
      }
    }
    reader.endObject();

    return new SentryEnvelopeHeader(sentryId);
  }
}
