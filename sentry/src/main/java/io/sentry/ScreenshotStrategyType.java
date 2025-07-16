package io.sentry;

import org.jetbrains.annotations.ApiStatus;

/** Enum representing the available screenshot strategies for replay recording. */
@ApiStatus.Internal
public enum ScreenshotStrategyType {
  /** Uses Canvas-based rendering for capturing screenshots */
  CANVAS,
  /** Uses PixelCopy API for capturing screenshots */
  PIXEL_COPY,
}
