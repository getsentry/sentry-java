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
    HashMap<String, Object> map = new HashMap<>();
    collect(reader, logger);
    final TokenMap root = ((TokenMap) getCurrentToken());
    if (root != null) {
      map = root.map;
    }
    return map;
  }

  private void collect(@NotNull JsonObjectReader reader, @NotNull ILogger logger) throws IOException {
    boolean done = false;
    JsonToken token = reader.peek();
    switch (token) {
      case BEGIN_ARRAY:
        reader.beginArray();
        addCurrentToken(new TokenArray());
        break;
      case END_ARRAY:
        reader.endArray();

        TokenArray tokenArrayArray = (TokenArray) getCurrentToken();
        removeCurrentToken(); // Array

        TokenName tokenNameArray = (TokenName) getCurrentToken();
        removeCurrentToken(); // Name

        TokenMap tokenMapArray = (TokenMap) getCurrentToken();

        if (tokenNameArray != null && tokenArrayArray != null && tokenMapArray != null) {
          tokenMapArray.map.put(tokenNameArray.name, tokenArrayArray.array);
        }
        break;
      case BEGIN_OBJECT:
        reader.beginObject();
        addCurrentToken(new TokenMap()); // Will be initial map if this is the first token added.
        break;
      case END_OBJECT:
        reader.endObject();

        if (isInitialToken()) {
          done = true;
        } else {
          TokenMap tokenMapMap = (TokenMap) getCurrentToken();
          removeCurrentToken(); // Map

          TokenName tokenNameMap = (TokenName) getCurrentToken();
          removeCurrentToken(); // Name

          TokenMap tokenMap = (TokenMap) getCurrentToken();
          if (tokenMapMap != null && tokenNameMap != null && tokenMap != null) {
            tokenMap.map.put(tokenNameMap.name, tokenMapMap.map);
          }
        }
        break;
      case NAME:
        addCurrentToken(new TokenName(reader.nextName()));
        break;
      case STRING:
        if (getCurrentToken() instanceof TokenName) {
          TokenName tokenNameString = (TokenName) getCurrentToken();
          removeCurrentToken();

          TokenMap tokenMapString = (TokenMap) getCurrentToken();
          tokenMapString.map.put(tokenNameString.name, reader.nextString());

        } else if (getCurrentToken() instanceof TokenArray) {
          TokenArray tokenArrayString = (TokenArray) getCurrentToken();
          tokenArrayString.array.add(reader.nextString());
        }
        break;
      case NUMBER:
        if (getCurrentToken() instanceof TokenName) {
          TokenName tokenNameNumber = (TokenName) getCurrentToken();
          removeCurrentToken(); // Name

          TokenMap tokenMapNumber = (TokenMap) getCurrentToken();
          tokenMapNumber.map.put(tokenNameNumber.name, nextNumber(reader));

        } else if (getCurrentToken() instanceof TokenArray) {
          TokenArray tokenArrayNumber = (TokenArray) getCurrentToken();
          tokenArrayNumber.array.add(nextNumber(reader));
        }
        break;
      case BOOLEAN:
        if (getCurrentToken() instanceof TokenName) {
          TokenName tokenNameBoolean = (TokenName) getCurrentToken();
          removeCurrentToken(); // Name

          TokenMap tokenMapBoolean = (TokenMap) getCurrentToken();
          tokenMapBoolean.map.put(tokenNameBoolean.name, reader.nextBoolean());

        } else if (getCurrentToken() instanceof TokenArray) {
          TokenArray tokenArrayBoolean = (TokenArray) getCurrentToken();
          tokenArrayBoolean.array.add(reader.nextBoolean());
        }
        break;
      case NULL:
        reader.nextNull();
        if (getCurrentToken() instanceof TokenName) {
          TokenName tokenNameNull = (TokenName) getCurrentToken();
          removeCurrentToken(); // Name

          TokenMap tokenMapNull = (TokenMap) getCurrentToken();
          tokenMapNull.map.put(tokenNameNull.name, null);

        } else if (getCurrentToken() instanceof TokenArray) {
          TokenArray tokenArrayNull = (TokenArray) getCurrentToken();
          tokenArrayNull.array.add(null);
        }
        break;
      case END_DOCUMENT:
        break;
    }

    if (!done) {
      collect(reader, logger);
    }
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

  private boolean isInitialToken() {
    return tokens.size() == 1;
  }
}
