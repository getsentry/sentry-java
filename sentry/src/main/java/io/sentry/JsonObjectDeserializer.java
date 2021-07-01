package io.sentry;

import io.sentry.vendor.gson.stream.JsonToken;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonObjectDeserializer {

  // Tokens

  interface Token {
    @NotNull Object getValue();
  }

  static final class TokenName implements Token {
    final String name;

    TokenName(@NotNull String name) {
      this.name = name;
    }

    @Override
    public @NotNull Object getValue() {
      return name;
    }
  }

  static final class TokenPrimitive implements Token {
    final Object value;
    TokenPrimitive(@NotNull Object value) {
      this.value = value;
    }

    @Override
    public @NotNull Object getValue() {
      return value;
    }
  }

  static final class TokenArray implements Token {
    final ArrayList<Object> array = new ArrayList<>();

    @Override
    public @NotNull Object getValue() {
      return array;
    }
  }

  static final class TokenMap implements Token {
    final HashMap<String, Object> map = new HashMap<>();

    @Override
    public @NotNull Object getValue() {
      return map;
    }
  }

  private final ArrayList<Token> tokens = new ArrayList<>();

  // Public API

  public @Nullable Object deserialize(@NotNull JsonObjectReader reader)
      throws IOException {
    parse(reader);
    final Token root = getCurrentToken();
    if (root != null) {
      return root.getValue();
    } else {
      return null;
    }
  }

  // Helper

  private void parse(@NotNull JsonObjectReader reader) throws IOException {
    boolean done = false;
    JsonToken token = reader.peek();
    switch (token) {
      case BEGIN_ARRAY:
        reader.beginArray();
        addCurrentToken(new TokenArray());
        break;
      case END_ARRAY:
        reader.endArray();

        if (hasOneToken()) {
          done = true;
        } else {
          TokenArray tokenArrayArray = (TokenArray) getCurrentToken();
          removeCurrentToken(); // Array

          TokenName tokenNameArray = (TokenName) getCurrentToken();
          removeCurrentToken(); // Name

          TokenMap tokenMapArray = (TokenMap) getCurrentToken();

          if (tokenNameArray != null && tokenArrayArray != null && tokenMapArray != null) {
            tokenMapArray.map.put(tokenNameArray.name, tokenArrayArray.array);
          }
        }
        break;
      case BEGIN_OBJECT:
        reader.beginObject();
        addCurrentToken(new TokenMap());
        break;
      case END_OBJECT:
        reader.endObject();

        if (hasOneToken()) {
          done = true;
        } else {
          TokenMap tokenMapMap = (TokenMap) getCurrentToken();
          removeCurrentToken(); // Map

          if (getCurrentToken() instanceof TokenName) {
            TokenName tokenNameMap = (TokenName) getCurrentToken();
            removeCurrentToken(); // Name

            TokenMap tokenMap = (TokenMap) getCurrentToken();
            if (tokenMapMap != null && tokenNameMap != null && tokenMap != null) {
              tokenMap.map.put(tokenNameMap.name, tokenMapMap.map);
            }
          } else if (getCurrentToken() instanceof TokenArray) {
            TokenArray tokenArrayObject = (TokenArray) getCurrentToken();
            if (tokenMapMap != null && tokenArrayObject != null) {
              tokenArrayObject.array.add(tokenMapMap.map);
            }
          }
        }
        break;
      case NAME:
        addCurrentToken(new TokenName(reader.nextName()));
        break;
      case STRING:
        if (getCurrentToken() == null) {
          addCurrentToken(new TokenPrimitive(reader.nextString()));
          done = true;
        } else if (getCurrentToken() instanceof TokenName) {
          TokenName tokenNameString = (TokenName) getCurrentToken();
          removeCurrentToken(); // Name

          TokenMap tokenMapString = (TokenMap) getCurrentToken();
          tokenMapString.map.put(tokenNameString.name, reader.nextString());

        } else if (getCurrentToken() instanceof TokenArray) {
          TokenArray tokenArrayString = (TokenArray) getCurrentToken();
          tokenArrayString.array.add(reader.nextString());
        }
        break;
      case NUMBER:
        if (getCurrentToken() == null) {
          addCurrentToken(new TokenPrimitive(nextNumber(reader)));
          done = true;
        } else if (getCurrentToken() instanceof TokenName) {
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
        if (getCurrentToken() == null) {
          addCurrentToken(new TokenPrimitive(reader.nextBoolean()));
          done = true;
        } else if (getCurrentToken() instanceof TokenName) {
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
        if (getCurrentToken() == null) {
          done = true;
        } if (getCurrentToken() instanceof TokenName) {
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
        done = true;
        break;
    }

    if (!done) {
      parse(reader);
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

  private @Nullable Token getCurrentToken() {
    if (tokens.isEmpty()) {
      return null;
    }
    return tokens.get(tokens.size() - 1);
  }

  private void addCurrentToken(Token token) {
    tokens.add(token);
  }

  private void removeCurrentToken() {
    if (tokens.isEmpty()) {
      return;
    }
    tokens.remove(tokens.size() - 1);
  }

  private boolean hasOneToken() {
    return tokens.size() == 1;
  }
}
