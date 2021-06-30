package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.sentry.vendor.gson.stream.JsonToken;

@ApiStatus.Internal
public final class JsonObjectDeserializer {

  private final ArrayList<JsonToken> tokenStack = new ArrayList<>();

  private @Nullable String currentName;
  private @Nullable ArrayList<Object> currentArray;

  public Map<String, Object> deserialize(@NotNull JsonObjectReader reader, @NotNull ILogger logger) throws IOException {
    Map<String, Object> map = new HashMap<>();

    JsonToken token = reader.peek();
    switch (token) {
      case BEGIN_ARRAY:
        reader.beginArray();
        currentArray = new ArrayList<>();
        addCurrentToken(JsonToken.BEGIN_ARRAY);
        break;
      case END_ARRAY:
        reader.endArray();
        if (currentArray != null) {
          map.put(currentName, new ArrayList<>(currentArray));
          currentArray.clear();
        }
        removeCurrentToken(); // Array
        removeCurrentToken(); // Name
        break;
      case BEGIN_OBJECT:
        reader.beginObject();
        addCurrentToken(JsonToken.BEGIN_OBJECT);
        break;
      case END_OBJECT:
        reader.endObject();
        removeCurrentToken();
        break;
      case NAME:
        currentName = reader.nextName();
        addCurrentToken(JsonToken.NAME);
        break;
      case STRING:
        if (currentToken() == JsonToken.NAME) {
          map.put(currentName, reader.nextString());
          currentName = null;
          removeCurrentToken();
        } else if (currentToken() == JsonToken.BEGIN_ARRAY && currentArray != null) {
          currentArray.add(reader.nextString());
        }
        break;
      case NUMBER:
        if (currentToken() == JsonToken.NAME) {
          map.put(currentName, nextNumber(reader));
          currentName = null;
          removeCurrentToken();
        } else if (currentToken() == JsonToken.BEGIN_ARRAY && currentArray != null) {
          currentArray.add(nextNumber(reader));
        }
        break;
      case BOOLEAN:
        if (currentToken() == JsonToken.NAME) {
          map.put(currentName, reader.nextBoolean());
          currentName = null;
          removeCurrentToken();
        } else if (currentToken() == JsonToken.BEGIN_ARRAY && currentArray != null) {
          currentArray.add(reader.nextBoolean());
        }
        break;
      case NULL:
        reader.nextNull();
        if (currentToken() == JsonToken.NAME) {
          map.put(currentName, null);
          currentName = null;
          removeCurrentToken();
        } else if (currentToken() == JsonToken.BEGIN_ARRAY && currentArray != null) {
          currentArray.add(null);
        }
        break;
      case END_DOCUMENT:
        break;
    }

    if (currentToken() != null) {
      map.putAll(deserialize(reader, logger));
    }

    return map;
  }

  private Object nextNumber(JsonObjectReader reader) throws IOException {
    try {
      return reader.nextInt();
    } catch (Exception exception) {
      // Need to try/fail as there are no int/double/long tokens.
    }
    try {
      return reader.nextDouble();
    } catch (Exception exception) {
      // Need to try/fail as there are no int/double/long tokens.
    }
    return reader.nextLong();
  }

  private @Nullable JsonToken currentToken() {
    if (tokenStack.isEmpty()) {
      return null;
    }
    return tokenStack.get(tokenStack.size() - 1);
  }

  private void addCurrentToken(JsonToken token) {
    tokenStack.add(token);
  }

  private void removeCurrentToken() {
    if (tokenStack.isEmpty()) {
      return;
    }
    tokenStack.remove(tokenStack.size() - 1);
  }
}
