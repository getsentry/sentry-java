package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Bridges the {@code Sentry.extendAppStart()} / {@code Sentry.finishAppStart()} / {@code
 * Sentry.getExtendedAppStartSpan()} static API to the Android implementation. The default
 * implementation ({@link NoOpAppStartExtender}) does nothing, so the API is a no-op on platforms
 * that don't provide an app start measurement.
 */
@ApiStatus.Internal
public interface IAppStartExtender {

  /**
   * Begins extending the app start. Intended to be called from {@code Application.onCreate} right
   * after SDK init. No-ops if the app start already finished, none is in progress, or it was
   * already extended (first call wins).
   */
  void extendAppStart();

  /**
   * Finishes the extended app start, allowing the app start transaction to complete. No-ops if the
   * app start was not extended or this was already called.
   */
  void finishAppStart();

  /**
   * Returns the active extended app start span to attach child spans to, or a no-op span when no
   * extension is active.
   */
  @NotNull
  ISpan getExtendedAppStartSpan();
}
