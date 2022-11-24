package io.sentry;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hint {

  static final class WeakHint<T> extends WeakReference<T> {
    public WeakHint(@Nullable T referent) {
      super(referent);
    }
  }

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

  private final @NotNull Map<String, Object> internalStorage = new HashMap<>();
  private final @NotNull List<Attachment> attachments = new ArrayList<>();
  private @Nullable Attachment screenshot = null;

  public static @NotNull Hint withAttachment(@Nullable Attachment attachment) {
    @NotNull final Hint hint = new Hint();
    hint.addAttachment(attachment);
    return hint;
  }

  public static @NotNull Hint withAttachments(@Nullable List<Attachment> attachments) {
    @NotNull final Hint hint = new Hint();
    hint.addAttachments(attachments);
    return hint;
  }

  public void set(@NotNull String name, @Nullable Object hint) {
    internalStorage.put(name, hint);
  }

  /**
   * Adds extra information to this hint.
   *
   * @param name the name of the hint
   * @param hint the hint value, internally stored as a WeakReference and automatically unwrapped
   *     when calling {@link #get(String)} {@link #getAs(String, Class)}
   */
  public void setWeak(@NotNull String name, @Nullable Object hint) {
    internalStorage.put(name, new WeakHint<>(hint));
  }

  @SuppressWarnings("unchecked")
  public @Nullable Object get(@NotNull String name) {
    final @Nullable Object value = internalStorage.get(name);
    if (value instanceof WeakHint) {
      return ((WeakHint<Object>) value).get();
    } else {
      return value;
    }
  }

  @SuppressWarnings("unchecked")
  public <T extends Object> @Nullable T getAs(@NotNull String name, @NotNull Class<T> clazz) {
    @Nullable Object hintValue = internalStorage.get(name);
    if (hintValue instanceof WeakHint) {
      hintValue = ((WeakHint<Object>) hintValue).get();
    }
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
    return new ArrayList<>(attachments);
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
