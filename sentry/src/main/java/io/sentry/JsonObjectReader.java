package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;

import io.sentry.vendor.gson.stream.JsonReader;

@ApiStatus.Internal
public final class JsonObjectReader extends JsonReader {

  public JsonObjectReader(Reader in) {
    super(in);
  }

  public @Nullable Map<String, Object> nextObjectOrNull(@NotNull JsonObjectReader reader) throws IOException {
    return new JsonObjectDeserializer().deserialize(reader);
  }
}
