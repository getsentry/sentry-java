package io.sentry;

import static io.sentry.util.IntegrationUtils.addIntegrationToSdkVersion;

import io.sentry.protocol.SdkVersion;
import io.sentry.util.SampleRateUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryReplayOptions extends SentryMaskingOptions {

  private static final String CUSTOM_MASKING_INTEGRATION_NAME = "ReplayCustomMasking";
  private volatile boolean customMaskingTracked = false;

  /**
   * Maximum size in bytes for network request/response bodies to be captured in replays. Bodies
   * larger than this will be truncated or replaced with a placeholder message. Aligned <a
   * href="https://github.com/getsentry/sentry-javascript/blob/98de756506705b60d1ca86cbbcfad3fd76062f8f/packages/replay-internal/src/constants.ts#L33">
   * with JS</a>
   */
  @ApiStatus.Internal public static final int MAX_NETWORK_BODY_SIZE = 150 * 1024;

  public enum SentryReplayQuality {
    /** Video Scale: 80% Bit Rate: 50.000 JPEG Compression: 10 */
    LOW(0.8f, 50_000, 10),

    /** Video Scale: 100% Bit Rate: 75.000 JPEG Compression: 30 */
    MEDIUM(1.0f, 75_000, 30),

    /** Video Scale: 100% Bit Rate: 100.000 JPEG Compression: 50 */
    HIGH(1.0f, 100_000, 50);

    /** The scale related to the window size (in dp) at which the replay will be created. */
    public final float sizeScale;

    /**
     * Defines the quality of the session replay. Higher bit rates have better replay quality, but
     * also affect the final payload size to transfer, defaults to 40kbps.
     */
    public final int bitRate;

    /** Defines the compression quality with which the screenshots are stored to disk. */
    public final int screenshotQuality;

    SentryReplayQuality(final float sizeScale, final int bitRate, final int screenshotQuality) {
      this.sizeScale = sizeScale;
      this.bitRate = bitRate;
      this.screenshotQuality = screenshotQuality;
    }

    public @NotNull String serializedName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * Indicates the percentage in which the replay for the session will be created. Specifying 0
   * means never, 1.0 means always. The value needs to be >= 0.0 and <= 1.0 The default is null
   * (disabled).
   */
  private @Nullable Double sessionSampleRate;

  /**
   * Indicates the percentage in which a 30 seconds replay will be send with error events.
   * Specifying 0 means never, 1.0 means always. The value needs to be >= 0.0 and <= 1.0. The
   * default is null (disabled).
   */
  private @Nullable Double onErrorSampleRate;

  /**
   * Defines the quality of the session replay. The higher the quality, the more accurate the replay
   * will be, but also more data to transfer and more CPU load, defaults to MEDIUM.
   */
  private SentryReplayQuality quality = SentryReplayQuality.MEDIUM;

  /**
   * Number of frames per second of the replay. The bigger the number, the more accurate the replay
   * will be, but also more data to transfer and more CPU load, defaults to 1fps.
   */
  private int frameRate = 1;

  /** The maximum duration of replays for error events, defaults to 30s. */
  private long errorReplayDuration = 30_000L;

  /** The maximum duration of the segment of a session replay, defaults to 5s. */
  private long sessionSegmentDuration = 5000L;

  /** The maximum duration of a full session replay, defaults to 1h. */
  private long sessionDuration = 60 * 60 * 1000L;

  /**
   * Whether to track the screen configuration (e.g. window size, orientation, etc.) automatically.
   * A valid configuration is required to capture a session replay. If set to false,
   * ReplayIntegration.onConfigurationChanged() must be called manually to update the configuration.
   *
   * <p>Defaults to true.
   */
  private boolean trackConfiguration = true;

  /**
   * SdkVersion object that contains the Sentry Client Name and its version. This object is only
   * applied to {@link SentryReplayEvent}s.
   */
  private @Nullable SdkVersion sdkVersion;

  /**
   * Turns debug mode on or off for Session Replay-specific code paths. If debug is enabled SDK will
   * attempt to print out useful debugging information if something goes wrong. Default is disabled.
   */
  private boolean debug = false;

  /**
   * The screenshot strategy to use for capturing screenshots during replay recording. Defaults to
   * {@link ScreenshotStrategyType#PIXEL_COPY}. If set to {@link ScreenshotStrategyType#CANVAS}, the
   * SDK will use the Canvas API to capture screenshots, which will always mask all Texts and
   * Bitmaps drawn on the screen, causing {@link #addMaskViewClass} and {@link #addUnmaskViewClass}
   * to be ignored.
   */
  @ApiStatus.Experimental
  private @NotNull ScreenshotStrategyType screenshotStrategy = ScreenshotStrategyType.PIXEL_COPY;

  /**
   * Capture request and response details for XHR and fetch requests that match the given URLs.
   * Default is empty (network details not collected).
   */
  private @NotNull List<String> networkDetailAllowUrls = Collections.emptyList();

  /**
   * Do not capture request and response details for these URLs. Takes precedence over
   * networkDetailAllowUrls. Default is empty.
   */
  private @NotNull List<String> networkDetailDenyUrls = Collections.emptyList();

  /**
   * Decide whether to capture request and response bodies for URLs defined in
   * networkDetailAllowUrls. Default is true, but capturing bodies requires at least one url
   * specified via {@link #setNetworkDetailAllowUrls(List)}.
   */
  private boolean networkCaptureBodies = true;

  /** Default headers that are always captured for URLs defined in networkDetailAllowUrls. */
  private static final @NotNull List<String> DEFAULT_HEADERS =
      Collections.unmodifiableList(Arrays.asList("Content-Type", "Content-Length", "Accept"));

  /**
   * Gets the default headers that are always captured for URLs defined in networkDetailAllowUrls.
   *
   * @return an unmodifiable list
   */
  @ApiStatus.Internal
  public static @NotNull List<String> getNetworkDetailsDefaultHeaders() {
    return DEFAULT_HEADERS;
  }

  /**
   * Additional request headers to capture for URLs defined in networkDetailAllowUrls. The default
   * headers (Content-Type, Content-Length, Accept) are always included in addition to these.
   */
  private @NotNull List<String> networkRequestHeaders = DEFAULT_HEADERS;

  /**
   * Additional response headers to capture for URLs defined in networkDetailAllowUrls. The default
   * headers (Content-Type, Content-Length, Accept) are always included in addition to these.
   */
  private @NotNull List<String> networkResponseHeaders = DEFAULT_HEADERS;

  public SentryReplayOptions(final boolean empty, final @Nullable SdkVersion sdkVersion) {
    if (!empty) {
      // Add default mask classes directly without setting usingCustomMasking flag
      maskViewClasses.add(TEXT_VIEW_CLASS_NAME);
      maskViewClasses.add(IMAGE_VIEW_CLASS_NAME);
      maskViewClasses.add(WEB_VIEW_CLASS_NAME);
      maskViewClasses.add(VIDEO_VIEW_CLASS_NAME);
      maskViewClasses.add(CAMERAX_PREVIEW_VIEW_CLASS_NAME);
      maskViewClasses.add(ANDROIDX_MEDIA_VIEW_CLASS_NAME);
      maskViewClasses.add(EXOPLAYER_CLASS_NAME);
      maskViewClasses.add(EXOPLAYER_STYLED_CLASS_NAME);
      this.sdkVersion = sdkVersion;
    }
  }

  public SentryReplayOptions(
      final @Nullable Double sessionSampleRate,
      final @Nullable Double onErrorSampleRate,
      final @Nullable SdkVersion sdkVersion) {
    this(false, sdkVersion);
    this.sessionSampleRate = sessionSampleRate;
    this.onErrorSampleRate = onErrorSampleRate;
    this.sdkVersion = sdkVersion;
  }

  @Nullable
  public Double getOnErrorSampleRate() {
    return onErrorSampleRate;
  }

  public boolean isSessionReplayEnabled() {
    return (getSessionSampleRate() != null && getSessionSampleRate() > 0);
  }

  public void setOnErrorSampleRate(final @Nullable Double onErrorSampleRate) {
    if (!SampleRateUtils.isValidSampleRate(onErrorSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + onErrorSampleRate
              + " is not valid. Use null to disable or values >= 0.0 and <= 1.0.");
    }
    this.onErrorSampleRate = onErrorSampleRate;
  }

  @Nullable
  public Double getSessionSampleRate() {
    return sessionSampleRate;
  }

  public boolean isSessionReplayForErrorsEnabled() {
    return (getOnErrorSampleRate() != null && getOnErrorSampleRate() > 0);
  }

  public void setSessionSampleRate(final @Nullable Double sessionSampleRate) {
    if (!SampleRateUtils.isValidSampleRate(sessionSampleRate)) {
      throw new IllegalArgumentException(
          "The value "
              + sessionSampleRate
              + " is not valid. Use null to disable or values >= 0.0 and <= 1.0.");
    }
    this.sessionSampleRate = sessionSampleRate;
  }

  @Override
  public void setMaskAllText(final boolean maskAllText) {
    if (maskAllText) {
      maskViewClasses.add(TEXT_VIEW_CLASS_NAME);
      unmaskViewClasses.remove(TEXT_VIEW_CLASS_NAME);
    } else {
      trackCustomMasking();
      unmaskViewClasses.add(TEXT_VIEW_CLASS_NAME);
      maskViewClasses.remove(TEXT_VIEW_CLASS_NAME);
    }
  }

  @Override
  public void setMaskAllImages(final boolean maskAllImages) {
    if (maskAllImages) {
      maskViewClasses.add(IMAGE_VIEW_CLASS_NAME);
      unmaskViewClasses.remove(IMAGE_VIEW_CLASS_NAME);
    } else {
      trackCustomMasking();
      unmaskViewClasses.add(IMAGE_VIEW_CLASS_NAME);
      maskViewClasses.remove(IMAGE_VIEW_CLASS_NAME);
    }
  }

  @Override
  public void addMaskViewClass(final @NotNull String className) {
    trackCustomMasking();
    this.maskViewClasses.add(className);
    this.unmaskViewClasses.remove(className);
  }

  @Override
  public void addUnmaskViewClass(final @NotNull String className) {
    trackCustomMasking();
    this.unmaskViewClasses.add(className);
    this.maskViewClasses.remove(className);
  }

  @ApiStatus.Internal
  public @NotNull SentryReplayQuality getQuality() {
    return quality;
  }

  public void setQuality(final @NotNull SentryReplayQuality quality) {
    this.quality = quality;
  }

  @ApiStatus.Internal
  public int getFrameRate() {
    return frameRate;
  }

  @ApiStatus.Internal
  public long getErrorReplayDuration() {
    return errorReplayDuration;
  }

  @ApiStatus.Internal
  public long getSessionSegmentDuration() {
    return sessionSegmentDuration;
  }

  @ApiStatus.Internal
  public long getSessionDuration() {
    return sessionDuration;
  }

  @Override
  public void trackCustomMasking() {
    if (!customMaskingTracked) {
      customMaskingTracked = true;
      addIntegrationToSdkVersion(CUSTOM_MASKING_INTEGRATION_NAME);
    }
  }

  @ApiStatus.Internal
  public boolean isTrackConfiguration() {
    return trackConfiguration;
  }

  @ApiStatus.Internal
  public void setTrackConfiguration(final boolean trackConfiguration) {
    this.trackConfiguration = trackConfiguration;
  }

  @ApiStatus.Internal
  public @Nullable SdkVersion getSdkVersion() {
    return sdkVersion;
  }

  @ApiStatus.Internal
  public void setSdkVersion(final @Nullable SdkVersion sdkVersion) {
    this.sdkVersion = sdkVersion;
  }

  /**
   * Check if debug mode is ON Default is OFF
   *
   * @return true if ON or false otherwise
   */
  public boolean isDebug() {
    return debug;
  }

  /**
   * Sets the debug mode to ON or OFF Default is OFF
   *
   * @param debug true if ON or false otherwise
   */
  public void setDebug(final boolean debug) {
    this.debug = debug;
  }

  /**
   * Gets the screenshot strategy used for capturing screenshots during replay recording.
   *
   * @return the screenshot strategy
   */
  @ApiStatus.Experimental
  public @NotNull ScreenshotStrategyType getScreenshotStrategy() {
    return screenshotStrategy;
  }

  /**
   * Sets the screenshot strategy to use for capturing screenshots during replay recording.
   *
   * @param screenshotStrategy the screenshot strategy to use
   */
  @ApiStatus.Experimental
  public void setScreenshotStrategy(final @NotNull ScreenshotStrategyType screenshotStrategy) {
    this.screenshotStrategy = screenshotStrategy;
  }

  /**
   * Gets the list of URLs for which network request and response details should be captured.
   *
   * @return the network detail allow URLs list
   */
  public @NotNull List<String> getNetworkDetailAllowUrls() {
    return networkDetailAllowUrls;
  }

  /**
   * Sets the list of URLs for which network request and response details should be captured.
   *
   * @param networkDetailAllowUrls the network detail allow URLs list
   */
  public void setNetworkDetailAllowUrls(final @NotNull List<String> networkDetailAllowUrls) {
    this.networkDetailAllowUrls =
        Collections.unmodifiableList(new ArrayList<>(networkDetailAllowUrls));
  }

  /**
   * Gets the list of URLs for which network request and response details should NOT be captured.
   *
   * @return the network detail deny URLs list
   */
  public @NotNull List<String> getNetworkDetailDenyUrls() {
    return networkDetailDenyUrls;
  }

  /**
   * Sets the list of URLs for which network request and response details should NOT be captured.
   * Takes precedence over networkDetailAllowUrls.
   *
   * @param networkDetailDenyUrls the network detail deny URLs list
   */
  public void setNetworkDetailDenyUrls(final @NotNull List<String> networkDetailDenyUrls) {
    this.networkDetailDenyUrls =
        Collections.unmodifiableList(new ArrayList<>(networkDetailDenyUrls));
  }

  /**
   * Gets whether to capture request and response bodies for URLs defined in networkDetailAllowUrls.
   *
   * @return true if network capture bodies is enabled, false otherwise
   */
  public boolean isNetworkCaptureBodies() {
    return networkCaptureBodies;
  }

  /**
   * Sets whether to capture request and response bodies for URLs defined in networkDetailAllowUrls.
   *
   * @param networkCaptureBodies true to enable network capture bodies, false otherwise
   */
  public void setNetworkCaptureBodies(final boolean networkCaptureBodies) {
    this.networkCaptureBodies = networkCaptureBodies;
  }

  /**
   * Gets all request headers to capture for URLs defined in networkDetailAllowUrls. This includes
   * both the default headers (Content-Type, Content-Length, Accept) and any additional headers.
   *
   * @return an unmodifiable list of the request headers to extract
   */
  public @NotNull List<String> getNetworkRequestHeaders() {
    return networkRequestHeaders;
  }

  /**
   * Sets request headers to capture for URLs defined in networkDetailAllowUrls. The default headers
   * (Content-Type, Content-Length, Accept) are always included automatically.
   *
   * @param networkRequestHeaders additional network request headers list
   */
  public void setNetworkRequestHeaders(final @NotNull List<String> networkRequestHeaders) {
    this.networkRequestHeaders = mergeHeaders(DEFAULT_HEADERS, networkRequestHeaders);
  }

  /**
   * Gets all response headers to capture for URLs defined in networkDetailAllowUrls. This includes
   * both the default headers (Content-Type, Content-Length, Accept) and any additional headers.
   *
   * @return an unmodifiable list of the response headers to extract
   */
  public @NotNull List<String> getNetworkResponseHeaders() {
    return networkResponseHeaders;
  }

  /**
   * Sets response headers to capture for URLs defined in networkDetailAllowUrls. The default
   * headers (Content-Type, Content-Length, Accept) are always included automatically.
   *
   * @param networkResponseHeaders the additional network response headers list
   */
  public void setNetworkResponseHeaders(final @NotNull List<String> networkResponseHeaders) {
    this.networkResponseHeaders = mergeHeaders(DEFAULT_HEADERS, networkResponseHeaders);
  }

  /**
   * Merges default headers with additional headers, removing duplicates while preserving order.
   *
   * @param defaultHeaders the default headers that are always included
   * @param additionalHeaders additional headers to merge
   * @return an unmodifiable list of merged headers
   */
  private static @NotNull List<String> mergeHeaders(
      final @NotNull List<String> defaultHeaders, final @NotNull List<String> additionalHeaders) {
    final Set<String> merged = new LinkedHashSet<>();
    merged.addAll(defaultHeaders);
    merged.addAll(additionalHeaders);
    return Collections.unmodifiableList(new ArrayList<>(merged));
  }
}
