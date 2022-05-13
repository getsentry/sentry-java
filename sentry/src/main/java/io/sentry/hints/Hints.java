package io.sentry.hints;

import static io.sentry.TypeCheckHint.SENTRY_ATTACHMENTS;

import io.sentry.Attachment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Hints {

  private final @NotNull Map<String, Object> internalStorage = new HashMap<String, Object>();

  public static @NotNull Hints withAttachment(@Nullable Attachment attachment) {
    @NotNull final Hints hints = new Hints();
    hints.getAttachmentContainer().add(attachment);
    return hints;
  }

  public static @NotNull Hints withAttachments(@Nullable List<Attachment> attachments) {
    @NotNull final Hints hints = new Hints();
    hints.getAttachmentContainer().addAll(attachments);
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

  public @NotNull AttachmentContainer getAttachmentContainer() {
    if (internalStorage.containsKey(SENTRY_ATTACHMENTS)) {
      AttachmentContainer container = getAs(SENTRY_ATTACHMENTS, AttachmentContainer.class);
      if (container != null) {
        return container;
      }
    }

    AttachmentContainer attachmentContainer = new AttachmentContainer();
    internalStorage.put(SENTRY_ATTACHMENTS, attachmentContainer);
    return attachmentContainer;
  }
}
