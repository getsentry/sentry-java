package io.sentry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class JsonObjectDeserializer {

  private interface NextValue {
    @Nullable
    Object nextValue() throws IOException;
  }

  private interface Token {
    @NotNull
    Object getValue();
  }

  private static final class TokenName implements Token {
    final String value;

    TokenName(@NotNull String value) {
      this.value = value;
    }

    @Override
    public @NotNull Object getValue() {
      return value;
    }
  }

  private static final class TokenPrimitive implements Token {
    final Object value;

    TokenPrimitive(@NotNull Object value) {
      this.value = value;
    }

    @Override
    public @NotNull Object getValue() {
      return value;
    }
  }

  private static final class TokenArray implements Token {
    final ArrayList<Object> value = new ArrayList<>();

    @Override
    public @NotNull Object getValue() {
      return value;
    }
  }

  private static final class TokenMap implements Token {
    final HashMap<String, Object> value = new HashMap<>();

    @Override
    public @NotNull Object getValue() {
      return value;
    }
  }

  private final ArrayList<Token> tokens = new ArrayList<>();

  // Public API

  public @Nullable Object deserialize(@NotNull JsonObjectReader reader) throws IOException {
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
    switch (reader.peek()) {
      case BEGIN_ARRAY:
        reader.beginArray();
        pushCurrentToken(new TokenArray());
        break;
      case END_ARRAY:
        reader.endArray();
        done = handleArrayOrMapEnd();
        break;
      case BEGIN_OBJECT:
        reader.beginObject();
        pushCurrentToken(new TokenMap());
        break;
      case END_OBJECT:
        reader.endObject();
        done = handleArrayOrMapEnd();
        break;
      case NAME:
        pushCurrentToken(new TokenName(reader.nextName()));
        break;
      case STRING:
        // avoid method refs on Android due to some issues with older AGP setups
        // noinspection Convert2MethodRef
        done = handlePrimitive(() -> reader.nextString());
        break;
      case NUMBER:
        done = handlePrimitive(() -> nextNumber(reader));
        break;
      case BOOLEAN:
        // avoid method refs on Android due to some issues with older AGP setups
        // noinspection Convert2MethodRef
        done = handlePrimitive(() -> reader.nextBoolean());
        break;
      case NULL:
        reader.nextNull();
        done = handlePrimitive(() -> null);
        break;
      case END_DOCUMENT:
        done = true;
        break;
    }
    if (!done) {
      parse(reader);
    }
  }

  private boolean handleArrayOrMapEnd() {
    if (hasOneToken()) {
      return true;
    } else {
      Token arrayOrMapToken = getCurrentToken(); // Array/Map
      popCurrentToken();

      if (getCurrentToken() instanceof TokenName) {
        TokenName tokenName = (TokenName) getCurrentToken();
        popCurrentToken();

        TokenMap tokenMap = (TokenMap) getCurrentToken();
        if (tokenName != null && arrayOrMapToken != null && tokenMap != null) {
          tokenMap.value.put(tokenName.value, arrayOrMapToken.getValue());
        }
      } else if (getCurrentToken() instanceof TokenArray) {
        TokenArray tokenArray = (TokenArray) getCurrentToken();
        if (arrayOrMapToken != null && tokenArray != null) {
          tokenArray.value.add(arrayOrMapToken.getValue());
        }
      }
      return false;
    }
  }

  private boolean handlePrimitive(NextValue callback) throws IOException {
    Object primitive = callback.nextValue();
    if (getCurrentToken() == null && primitive != null) {
      pushCurrentToken(new TokenPrimitive(primitive));
      return true;
    } else if (getCurrentToken() instanceof TokenName) {
      TokenName tokenNameNumber = (TokenName) getCurrentToken();
      popCurrentToken();

      TokenMap tokenMapNumber = (TokenMap) getCurrentToken();
      tokenMapNumber.value.put(tokenNameNumber.value, primitive);

    } else if (getCurrentToken() instanceof TokenArray) {
      TokenArray tokenArrayNumber = (TokenArray) getCurrentToken();
      tokenArrayNumber.value.add(primitive);
    }
    return false;
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

  private void pushCurrentToken(Token token) {
    tokens.add(token);
  }

  private void popCurrentToken() {
    if (tokens.isEmpty()) {
      return;
    }
    tokens.remove(tokens.size() - 1);
  }

  private boolean hasOneToken() {
    return tokens.size() == 1;
  }
}
