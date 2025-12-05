package io.sentry;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public final class JsonRawString {
  private final @NotNull String value;

  public JsonRawString(@NotNull String value) {
    this.value = value;
  }

  public @NotNull String getValue() {
    return value;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof JsonRawString)) return false;
    JsonRawString that = (JsonRawString) o;
    return value.equals(that.value);
  }

  @Override
  public String toString() {
    return "JsonRawString{" + "value='" + value + '\'' + '}';
  }
}
