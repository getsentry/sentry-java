package io.sentry.hints;

import io.sentry.Attachment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hints {

  private final @NotNull Map<String, Object> internalStorage = new HashMap<String, Object>();
  private final @NotNull List<Attachment> attachments = new CopyOnWriteArrayList<>();

  public static @NotNull Hints withAttachment(@Nullable Attachment attachment) {
    @NotNull final Hints hints = new Hints();
    hints.addAttachment(attachment);
    return hints;
  }

  public static @NotNull Hints withAttachments(@Nullable List<Attachment> attachments) {
    @NotNull final Hints hints = new Hints();
    hints.addAttachments(attachments);
    return hints;
  }

  public Hints() {
    primitiveMappings = new HashMap<>();
    primitiveMappings.put("boolean", Boolean.class);
    primitiveMappings.put("char", Character.class);
    primitiveMappings.put("byte", Byte.class);
    primitiveMappings.put("short", Short.class);
    primitiveMappings.put("int", Integer.class);
    primitiveMappings.put("long", Long.class);
    primitiveMappings.put("float", Float.class);
    primitiveMappings.put("double", Double.class);
  }

  public void set(@NotNull String hintType, @Nullable Object hint) {
    internalStorage.put(hintType, hint);
  }

  public @Nullable Object get(@NotNull String hintName) {
    return internalStorage.get(hintName);
  }

  @SuppressWarnings("unchecked")
  public <T extends Object> @Nullable T getAs(@NotNull String hintName, @NotNull Class<T> clazz) {
    Object hintValue = internalStorage.get(hintName);

    if (clazz.isInstance(hintValue)) {
      return (T) hintValue;
    } else if (isCastablePrimitive(hintValue, clazz)) {
      return (T) hintValue;
    } else {
      return null;
    }
  }

  public void remove(@NotNull String hintName) {
    internalStorage.remove(hintName);
  }

  public void addAttachment(@Nullable Attachment attachment) {
    if (attachment != null) {
      attachments.add(attachment);
    }
  }

  @SuppressWarnings("unchecked")
  public void addAttachments(@Nullable List<Attachment> attachments) {
    if (attachments != null) {
      this.attachments.addAll(attachments);
    }
  }

  @SuppressWarnings("unchecked")
  public @NotNull List<Attachment> getAttachments() {
    return new CopyOnWriteArrayList<>(attachments);
  }

  public void replaceAttachments(@Nullable List<Attachment> attachments) {
    clear();
    addAttachments(attachments);
  }

  public void clear() {
    attachments.clear();
  }

  private final Map<String, Class<?>> primitiveMappings;

  private boolean isCastablePrimitive(@Nullable Object hintValue, @NotNull Class<?> clazz) {
    Class<?> nonPrimitiveClass = primitiveMappings.get(clazz.getCanonicalName());
    return hintValue != null
        && clazz.isPrimitive()
        && nonPrimitiveClass != null
        && nonPrimitiveClass.isInstance(hintValue);
  }
}
