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

  // Tokens

  static final class TokenName {
    final String name;
    TokenName(@NotNull String name) {
      this.name = name;
    }
  }

  static final class TokenArray {
    final ArrayList<Object> array = new ArrayList<>();;
  }

  static final class TokenMap {
    final HashMap<String, Object> map = new HashMap<>();
  }

  private final ArrayList<Object> tokens = new ArrayList<>();

  public Map<String, Object> deserialize(@NotNull JsonObjectReader reader, @NotNull ILogger logger) throws IOException {
    final HashMap<String, Object> map = new HashMap<>();

    JsonToken token = reader.peek();
    switch (token) {
      case BEGIN_ARRAY:
        reader.beginArray();
        addCurrentToken(new TokenArray());
        break;
      case END_ARRAY:
        reader.endArray();

        TokenArray tokenArray = (TokenArray) getCurrentToken();
        removeCurrentToken(); // Array
        TokenName tokenName = (TokenName) getCurrentToken();
        removeCurrentToken(); // Name

        if (tokenName != null && tokenArray != null) {
          map.put(tokenName.name, tokenArray.array);
        }
        break;
      case BEGIN_OBJECT:
        reader.beginObject();
        addCurrentToken(JsonToken.BEGIN_OBJECT);
        break;
      case END_OBJECT:
        reader.endObject();
        removeCurrentToken(); // Map
        removeCurrentToken(); // Name or Nothing
        break;
      case NAME:
        addCurrentToken(new TokenName(reader.nextName()));
        break;
      case STRING:
        if (getCurrentToken() instanceof TokenName) {
          TokenName tokenNameString = (TokenName) getCurrentToken();
          map.put(tokenNameString.name, reader.nextString());
          removeCurrentToken(); // Name
        } else if (getCurrentToken() instanceof TokenArray) {
          TokenArray tokenArrayString = (TokenArray) getCurrentToken();
          tokenArrayString.array.add(reader.nextString());
        }
        break;
      case NUMBER:
        if (getCurrentToken() instanceof TokenName) {
          TokenName tokenNameNumber = (TokenName) getCurrentToken();
          map.put(tokenNameNumber.name, nextNumber(reader));
          removeCurrentToken(); // Name
        } else if (getCurrentToken() instanceof TokenArray) {
          TokenArray tokenArrayNumber = (TokenArray) getCurrentToken();
          tokenArrayNumber.array.add(nextNumber(reader));
        }
        break;
      case BOOLEAN:
        if (getCurrentToken() instanceof TokenName) {
          TokenName tokenNameBoolean = (TokenName) getCurrentToken();
          map.put(tokenNameBoolean.name, reader.nextBoolean());
          removeCurrentToken(); // Name
        } else if (getCurrentToken() instanceof TokenArray) {
          TokenArray tokenArrayBoolean = (TokenArray) getCurrentToken();
          tokenArrayBoolean.array.add(reader.nextBoolean());
        }
        break;
      case NULL:
        reader.nextNull();
        if (getCurrentToken() instanceof TokenName) {
          TokenName tokenNameNull = (TokenName) getCurrentToken();
          map.put(tokenNameNull.name, null);
          removeCurrentToken(); // Name
        } else if (getCurrentToken() instanceof TokenArray) {
          TokenArray tokenArrayNull = (TokenArray) getCurrentToken();
          tokenArrayNull.array.add(null);
        }
        break;
      case END_DOCUMENT:
        break;
    }

    if (getCurrentToken() != null) {
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

  private @Nullable Object getCurrentToken() {
    if (tokens.isEmpty()) {
      return null;
    }
    return tokens.get(tokens.size() - 1);
  }

  private void addCurrentToken(Object token) {
    tokens.add(token);
  }

  private void removeCurrentToken() {
    if (tokens.isEmpty()) {
      return;
    }
    tokens.remove(tokens.size() - 1);
  }
}
