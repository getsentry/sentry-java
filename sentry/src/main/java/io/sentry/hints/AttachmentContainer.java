package io.sentry.hints;

import io.sentry.Attachment;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AttachmentContainer {

  private final @NotNull List<Attachment> internalStorage = new CopyOnWriteArrayList<>();

  public void add(@Nullable Attachment attachment) {
    if (attachment != null) {
      internalStorage.add(attachment);
    }
  }

  @SuppressWarnings("unchecked")
  public void addAll(@Nullable List<Attachment> attachments) {
    if (attachments != null) {
      internalStorage.addAll(attachments);
    }
  }

  @SuppressWarnings("unchecked")
  public @NotNull List<Attachment> getAll() {
    return new CopyOnWriteArrayList<>(internalStorage);
  }

  public void replaceAll(@Nullable List<Attachment> attachments) {
    clear();
    addAll(attachments);
  }

  public void clear() {
    internalStorage.clear();
  }
}
