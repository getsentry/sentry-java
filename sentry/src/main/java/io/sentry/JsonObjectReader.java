package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import java.io.Reader;
import io.sentry.vendor.gson.stream.JsonReader;

@ApiStatus.Internal
public final class JsonObjectReader extends JsonReader {

  public JsonObjectReader(Reader in) {
    super(in);
  }


}
