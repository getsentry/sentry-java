package io.sentry.hints;

import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hints {

  private final @NotNull Map<String, Object> internalStorage = new HashMap<String, Object>();

  public void set(@NotNull String hintType, @Nullable Object hint) {
    internalStorage.put(hintType, hint);
  }

  public @Nullable Object get(@NotNull String hintType) {
    return internalStorage.get(hintType);
  }

  // TODO maybe not public
  public void remove(@NotNull String hintType) {
    internalStorage.remove(hintType);
  }

  // TODO addAttachment(one)
  // TODO getAttachments(): List
  // TODO setAttachments(list)
  // TODO clearAttachments()
}
