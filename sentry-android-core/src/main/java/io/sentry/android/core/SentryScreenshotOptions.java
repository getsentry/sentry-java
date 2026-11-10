package io.sentry.android.core;

import io.sentry.SentryMaskingOptions;

/**
 * Screenshot masking options for error screenshots. Extends the base {@link SentryMaskingOptions}
 * with screenshot-specific defaults.
 *
 * <p>By default, all text and images are masked. Disable masking by calling {@link
 * #setMaskAllText(boolean)} and/or {@link #setMaskAllImages(boolean)} with {@code false}.
 *
 * <p>Note: Screenshot masking requires the {@code sentry-android-replay} module to be present at
 * runtime. If masking is enabled but the replay module is not available, no screenshot is captured
 * to avoid leaking sensitive data.
 */
public final class SentryScreenshotOptions extends SentryMaskingOptions {

  public SentryScreenshotOptions() {
    setMaskAllText(true);
    setMaskAllImages(true);
  }

  @Override
  public void trackCustomMasking() {
    // No-op for screenshots, custom masking tracking is only relevant for session replay.
  }

  /**
   * {@inheritDoc}
   *
   * <p>When enabling image masking for screenshots, this also adds masking for WebView, VideoView,
   * and media player views (ExoPlayer, Media3) since they may contain sensitive content.
   */
  @Override
  public void setMaskAllImages(final boolean maskAllImages) {
    super.setMaskAllImages(maskAllImages);
    if (maskAllImages) {
      addSensitiveViewClasses();
    } else {
      removeSensitiveViewClasses();
    }
  }

  private void addSensitiveViewClasses() {
    addMaskViewClass(WEB_VIEW_CLASS_NAME);
    addMaskViewClass(VIDEO_VIEW_CLASS_NAME);
    addMaskViewClass(CAMERAX_PREVIEW_VIEW_CLASS_NAME);
    addMaskViewClass(ANDROIDX_MEDIA_VIEW_CLASS_NAME);
    addMaskViewClass(EXOPLAYER_CLASS_NAME);
    addMaskViewClass(EXOPLAYER_STYLED_CLASS_NAME);
  }

  private void removeSensitiveViewClasses() {
    getMaskViewClasses().remove(WEB_VIEW_CLASS_NAME);
    getMaskViewClasses().remove(VIDEO_VIEW_CLASS_NAME);
    getMaskViewClasses().remove(CAMERAX_PREVIEW_VIEW_CLASS_NAME);
    getMaskViewClasses().remove(ANDROIDX_MEDIA_VIEW_CLASS_NAME);
    getMaskViewClasses().remove(EXOPLAYER_CLASS_NAME);
    getMaskViewClasses().remove(EXOPLAYER_STYLED_CLASS_NAME);
  }
}
