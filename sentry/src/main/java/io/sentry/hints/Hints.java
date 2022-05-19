package io.sentry.hints;

import io.sentry.Attachment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hints {

  private static final @NotNull Map<String, Class<?>> PRIMITIVE_MAPPINGS;

  static {
    PRIMITIVE_MAPPINGS = new HashMap<>();
    PRIMITIVE_MAPPINGS.put("boolean", Boolean.class);
    PRIMITIVE_MAPPINGS.put("char", Character.class);
    PRIMITIVE_MAPPINGS.put("byte", Byte.class);
    PRIMITIVE_MAPPINGS.put("short", Short.class);
    PRIMITIVE_MAPPINGS.put("int", Integer.class);
    PRIMITIVE_MAPPINGS.put("long", Long.class);
    PRIMITIVE_MAPPINGS.put("float", Float.class);
    PRIMITIVE_MAPPINGS.put("double", Double.class);
  }

  private final @NotNull Map<String, Object> internalStorage = new HashMap<String, Object>();
  private final @NotNull List<Attachment> attachments = new CopyOnWriteArrayList<>();
  private @Nullable Attachment screenshot = null;

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

  public void set(@NotNull String name, @Nullable Object hint) {
    internalStorage.put(name, hint);
  }

  public @Nullable Object get(@NotNull String name) {
    return internalStorage.get(name);
  }

  @SuppressWarnings("unchecked")
  public <T extends Object> @Nullable T getAs(@NotNull String name, @NotNull Class<T> clazz) {
    Object hintValue = internalStorage.get(name);

    if (clazz.isInstance(hintValue)) {
      return (T) hintValue;
    } else if (isCastablePrimitive(hintValue, clazz)) {
      return (T) hintValue;
    } else {
      return null;
    }
  }

  public void remove(@NotNull String name) {
    internalStorage.remove(name);
  }

  public void addAttachment(@Nullable Attachment attachment) {
    if (attachment != null) {
      attachments.add(attachment);
    }
  }

  public void addAttachments(@Nullable List<Attachment> attachments) {
    if (attachments != null) {
      this.attachments.addAll(attachments);
    }
  }

  public @NotNull List<Attachment> getAttachments() {
    return new CopyOnWriteArrayList<>(attachments);
  }

  public void replaceAttachments(@Nullable List<Attachment> attachments) {
    clearAttachments();
    addAttachments(attachments);
  }

  public void clearAttachments() {
    attachments.clear();
  }

  public void setScreenshot(@Nullable Attachment screenshot) {
    this.screenshot = screenshot;
  }

  public @Nullable Attachment getScreenshot() {
    return screenshot;
  }

  private boolean isCastablePrimitive(@Nullable Object hintValue, @NotNull Class<?> clazz) {
    Class<?> nonPrimitiveClass = PRIMITIVE_MAPPINGS.get(clazz.getCanonicalName());
    return hintValue != null
        && clazz.isPrimitive()
        && nonPrimitiveClass != null
        && nonPrimitiveClass.isInstance(hintValue);
  }
}
