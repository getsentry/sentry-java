package io.sentry.android.core;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import io.sentry.core.DateUtils;
import java.lang.reflect.Type;
import java.util.Date;

class DateDeserializerAdapter implements JsonDeserializer<Date> {
  @Override
  public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    return json == null ? null : DateUtils.getDateTime(json.getAsString());
  }
}
