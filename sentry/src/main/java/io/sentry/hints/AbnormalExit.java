package io.sentry.hints;

import org.jetbrains.annotations.Nullable;

/** Marker interface for Sessions experiencing abnormal status */
public interface AbnormalExit {
  @Nullable
  String mechanism();
}
