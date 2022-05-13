package io.sentry.hints;

import static io.sentry.TypeCheckHint.SENTRY_ATTACHMENTS;

import io.sentry.Attachment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hints {

  private final @NotNull Map<String, Object> internalStorage = new HashMap<String, Object>();

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
    } else {
      return null;
    }
  }

  public void remove(@NotNull String hintName) {
    internalStorage.remove(hintName);
  }

  @SuppressWarnings("unchecked")
  public @NotNull List<Attachment> getAttachments() {
    List<Attachment> attachments = getAs(SENTRY_ATTACHMENTS, List.class);
    if (attachments != null) {
      return new CopyOnWriteArrayList<>(attachments);
    }

    return new CopyOnWriteArrayList<>();
  }

  public void replaceAttachments(@Nullable List<Attachment> attachments) {
    clearAttachments();
    addAttachments(attachments);
  }

  public void clearAttachments() {
    internalStorage.put(SENTRY_ATTACHMENTS, new CopyOnWriteArrayList<Attachment>());
  }

  @SuppressWarnings("unchecked")
  public void addAttachment(@Nullable Attachment attachment) {
    if (attachment == null) {
      return;
    }

    List<Attachment> existingAttachments = getAs(SENTRY_ATTACHMENTS, List.class);
    if (existingAttachments != null) {
      existingAttachments.add(attachment);
      return;
    }

    List<Attachment> attachments = new CopyOnWriteArrayList<>();
    attachments.add(attachment);
    internalStorage.put(SENTRY_ATTACHMENTS, attachments);
  }

  @SuppressWarnings("unchecked")
  public void addAttachments(@Nullable List<Attachment> attachments) {
    if (attachments == null) {
      return;
    }

    List<Attachment> existingAttachments = getAs(SENTRY_ATTACHMENTS, List.class);
    if (existingAttachments != null) {
      existingAttachments.addAll(attachments);
      return;
    }

    internalStorage.put(SENTRY_ATTACHMENTS, new CopyOnWriteArrayList<>(attachments));
  }
}
