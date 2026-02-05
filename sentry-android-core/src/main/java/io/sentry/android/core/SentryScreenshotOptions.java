package io.sentry.android.core;

import io.sentry.SentryMaskingOptions;

/**
 * Screenshot masking options for error screenshots. Extends the base {@link SentryMaskingOptions}
 * with screenshot-specific defaults.
 *
 * <p>By default, masking is disabled for screenshots. Enable masking by calling {@link
 * #setMaskAllText(boolean)} and/or {@link #setMaskAllImages(boolean)}.
 *
 * <p>Note: Screenshot masking requires the {@code sentry-android-replay} module to be present at
 * runtime. If the replay module is not available, screenshots will be captured without masking.
 */
public final class SentryScreenshotOptions extends SentryMaskingOptions {

  public SentryScreenshotOptions() {
    // Default to NO masking until next major version.
    // maskViewClasses starts empty, so nothing is masked by default.
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
    }
  }

  private void addSensitiveViewClasses() {
    addMaskViewClass(WEB_VIEW_CLASS_NAME);
    addMaskViewClass(VIDEO_VIEW_CLASS_NAME);
    addMaskViewClass(ANDROIDX_MEDIA_VIEW_CLASS_NAME);
    addMaskViewClass(EXOPLAYER_CLASS_NAME);
    addMaskViewClass(EXOPLAYER_STYLED_CLASS_NAME);
  }
}
