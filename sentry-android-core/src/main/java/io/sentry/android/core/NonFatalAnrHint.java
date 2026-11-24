package io.sentry.android.core;

import io.sentry.hints.DiskFlushNotification;
import io.sentry.protocol.SentryId;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Type-check hint of non-fatal ANR events. Of the {@code io.sentry.hints} interfaces it implements
 * only {@link DiskFlushNotification}, so the session is not ended as abnormal for it.
 */
@ApiStatus.Internal
public final class NonFatalAnrHint implements DiskFlushNotification {

  private final @Nullable Runnable onStoredOnDisk;
  private final @NotNull AtomicBoolean flushed = new AtomicBoolean(false);

  /**
   * @param onStoredOnDisk runs once, on the transport thread, after the envelope of the event was
   *     stored on disk. The transport also calls it when a rate limit drops the event. Null if the
   *     event needs no action on disk.
   */
  public NonFatalAnrHint(final @Nullable Runnable onStoredOnDisk) {
    this.onStoredOnDisk = onStoredOnDisk;
  }

  @Override
  public void markFlushed() {
    if (onStoredOnDisk != null && flushed.compareAndSet(false, true)) {
      onStoredOnDisk.run();
    }
  }

  @Override
  public boolean isFlushable(final @Nullable SentryId eventId) {
    return true;
  }

  @Override
  public void setFlushable(final @NotNull SentryId eventId) {}
}
