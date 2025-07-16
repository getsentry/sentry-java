package io.sentry;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SentryReplayOptionsTest {

  @Test
  public void testDefaultScreenshotStrategy() {
    SentryReplayOptions options = new SentryReplayOptions(false, null);
    assertEquals(ScreenshotStrategyType.PIXEL_COPY, options.getScreenshotStrategy());
  }

  @Test
  public void testSetScreenshotStrategyToCanvas() {
    SentryReplayOptions options = new SentryReplayOptions(false, null);
    options.setScreenshotStrategy(ScreenshotStrategyType.CANVAS);
    assertEquals(ScreenshotStrategyType.CANVAS, options.getScreenshotStrategy());
  }

  @Test
  public void testSetScreenshotStrategyToPixelCopy() {
    SentryReplayOptions options = new SentryReplayOptions(false, null);
    options.setScreenshotStrategy(ScreenshotStrategyType.PIXEL_COPY);
    assertEquals(ScreenshotStrategyType.PIXEL_COPY, options.getScreenshotStrategy());
  }
}
