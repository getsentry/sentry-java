package io.sentry;

import io.sentry.protocol.SdkVersion;
import io.sentry.util.SampleRateUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class SentryReplayOptions {

  public static final String TEXT_VIEW_CLASS_NAME = "android.widget.TextView";
  public static final String IMAGE_VIEW_CLASS_NAME = "android.widget.ImageView";
  public static final String WEB_VIEW_CLASS_NAME = "android.webkit.WebView";
  public static final String VIDEO_VIEW_CLASS_NAME = "android.widget.VideoView";
  public static final String ANDROIDX_MEDIA_VIEW_CLASS_NAME = "androidx.media3.ui.PlayerView";
  public static final String EXOPLAYER_CLASS_NAME = "com.google.android.exoplayer2.ui.PlayerView";
  public static final String EXOPLAYER_STYLED_CLASS_NAME =
      "com.google.android.exoplayer2.ui.StyledPlayerView";

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
   * Mask all views with the specified class names. The class name is the fully qualified class name
   * of the view, e.g. android.widget.TextView. The subclasses of the specified classes will be
   * masked as well.
   *
   * <p>If you're using an obfuscation tool, make sure to add the respective proguard rules to keep
   * the class names.
   *
   * <p>Default is empty.
   */
  private Set<String> maskViewClasses = new CopyOnWriteArraySet<>();

  /**
   * Ignore all views with the specified class names from masking. The class name is the fully
   * qualified class name of the view, e.g. android.widget.TextView. The subclasses of the specified
   * classes will be ignored as well.
   *
   * <p>If you're using an obfuscation tool, make sure to add the respective proguard rules to keep
   * the class names.
   *
   * <p>Default is empty.
   */
  private Set<String> unmaskViewClasses = new CopyOnWriteArraySet<>();

  /** The class name of the view container that masks all of its children. */
  private @Nullable String maskViewContainerClass = null;

  /** The class name of the view container that unmasks its direct children. */
  private @Nullable String unmaskViewContainerClass = null;

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
  private @NotNull String[] networkDetailAllowUrls = new String[0];

  /**
   * Do not capture request and response details for these URLs. Takes precedence over
   * networkDetailAllowUrls. Default is empty.
   */
  private @NotNull String[] networkDetailDenyUrls = new String[0];

  /**
   * Decide whether to capture request and response bodies for URLs defined in
   * networkDetailAllowUrls. Default is true, but capturing bodies requires at least one url
   * specified via {@link #setNetworkDetailAllowUrls(String[])}.
   */
  private boolean networkCaptureBodies = true;

  /** Default headers that are always captured for URLs defined in networkDetailAllowUrls. */
  private static final @NotNull String[] DEFAULT_HEADERS =
      new String[] {"Content-Type", "Content-Length", "Accept"};

  /**
   * Gets the default headers that are always captured for URLs defined in networkDetailAllowUrls.
   * Returns a defensive copy to prevent modification.
   */
  @ApiStatus.Internal
  public static @NotNull String[] getNetworkDetailsDefaultHeaders() {
    return DEFAULT_HEADERS.clone();
  }

  /**
   * Additional request headers to capture for URLs defined in networkDetailAllowUrls. The default
   * headers (Content-Type, Content-Length, Accept) are always included in addition to these.
   */
  private @NotNull List<String> networkRequestHeaders = new ArrayList<>();

  /**
   * Additional response headers to capture for URLs defined in networkDetailAllowUrls. The default
   * headers (Content-Type, Content-Length, Accept) are always included in addition to these.
   */
  private @NotNull List<String> networkResponseHeaders = new ArrayList<>();

  public SentryReplayOptions(final boolean empty, final @Nullable SdkVersion sdkVersion) {
    if (!empty) {
      setMaskAllText(true);
      setMaskAllImages(true);
      maskViewClasses.add(WEB_VIEW_CLASS_NAME);
      maskViewClasses.add(VIDEO_VIEW_CLASS_NAME);
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

  /**
   * Mask all text content. Draws a rectangle of text bounds with text color on top. By default only
   * views extending TextView are masked.
   *
   * <p>Default is enabled.
   */
  public void setMaskAllText(final boolean maskAllText) {
    if (maskAllText) {
      addMaskViewClass(TEXT_VIEW_CLASS_NAME);
      unmaskViewClasses.remove(TEXT_VIEW_CLASS_NAME);
    } else {
      addUnmaskViewClass(TEXT_VIEW_CLASS_NAME);
      maskViewClasses.remove(TEXT_VIEW_CLASS_NAME);
    }
  }

  /**
   * Mask all image content. Draws a rectangle of image bounds with image's dominant color on top.
   * By default only views extending ImageView with BitmapDrawable or custom Drawable type are
   * masked. ColorDrawable, InsetDrawable, VectorDrawable are all considered non-PII, as they come
   * from the apk.
   *
   * <p>Default is enabled.
   */
  public void setMaskAllImages(final boolean maskAllImages) {
    if (maskAllImages) {
      addMaskViewClass(IMAGE_VIEW_CLASS_NAME);
      unmaskViewClasses.remove(IMAGE_VIEW_CLASS_NAME);
    } else {
      addUnmaskViewClass(IMAGE_VIEW_CLASS_NAME);
      maskViewClasses.remove(IMAGE_VIEW_CLASS_NAME);
    }
  }

  @NotNull
  public Set<String> getMaskViewClasses() {
    return this.maskViewClasses;
  }

  public void addMaskViewClass(final @NotNull String className) {
    this.maskViewClasses.add(className);
  }

  @NotNull
  public Set<String> getUnmaskViewClasses() {
    return this.unmaskViewClasses;
  }

  public void addUnmaskViewClass(final @NotNull String className) {
    this.unmaskViewClasses.add(className);
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

  @ApiStatus.Internal
  public void setMaskViewContainerClass(@NotNull String containerClass) {
    addMaskViewClass(containerClass);
    maskViewContainerClass = containerClass;
  }

  @ApiStatus.Internal
  public void setUnmaskViewContainerClass(@NotNull String containerClass) {
    unmaskViewContainerClass = containerClass;
  }

  @ApiStatus.Internal
  public @Nullable String getMaskViewContainerClass() {
    return maskViewContainerClass;
  }

  @ApiStatus.Internal
  public @Nullable String getUnmaskViewContainerClass() {
    return unmaskViewContainerClass;
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
   * Gets the array of URLs for which network request and response details should be captured.
   *
   * @return the network detail allow URLs array
   */
  public @NotNull String[] getNetworkDetailAllowUrls() {
    return networkDetailAllowUrls;
  }

  /**
   * Sets the array of URLs for which network request and response details should be captured.
   *
   * @param networkDetailAllowUrls the network detail allow URLs array
   */
  public void setNetworkDetailAllowUrls(final @NotNull String[] networkDetailAllowUrls) {
    this.networkDetailAllowUrls = networkDetailAllowUrls;
  }

  /**
   * Gets the array of URLs for which network request and response details should NOT be captured.
   *
   * @return the network detail deny URLs array
   */
  public @NotNull String[] getNetworkDetailDenyUrls() {
    return networkDetailDenyUrls;
  }

  /**
   * Sets the array of URLs for which network request and response details should NOT be captured.
   * Takes precedence over networkDetailAllowUrls.
   *
   * @param networkDetailDenyUrls the network detail deny URLs array
   */
  public void setNetworkDetailDenyUrls(final @NotNull String[] networkDetailDenyUrls) {
    this.networkDetailDenyUrls = networkDetailDenyUrls;
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
   * @return the complete network request headers array
   */
  public @NotNull String[] getNetworkRequestHeaders() {
    return mergeHeaders(DEFAULT_HEADERS, networkRequestHeaders);
  }

  /**
   * Sets additional request headers to capture for URLs defined in networkDetailAllowUrls. The
   * default headers (Content-Type, Content-Length, Accept) are always included automatically.
   *
   * @param networkRequestHeaders additional network request headers list
   */
  public void setNetworkRequestHeaders(final @NotNull List<String> networkRequestHeaders) {
    this.networkRequestHeaders = new ArrayList<>(networkRequestHeaders);
  }

  /**
   * Gets all response headers to capture for URLs defined in networkDetailAllowUrls. This includes
   * both the default headers (Content-Type, Content-Length, Content-Encoding) and any additional
   * headers.
   *
   * @return the complete network response headers array
   */
  public @NotNull String[] getNetworkResponseHeaders() {
    return mergeHeaders(DEFAULT_HEADERS, networkResponseHeaders);
  }

  /**
   * Sets additional response headers to capture for URLs defined in networkDetailAllowUrls. The
   * default headers (Content-Type, Content-Length, Accept) are always included automatically.
   *
   * @param networkResponseHeaders the additional network response headers list
   */
  public void setNetworkResponseHeaders(final @NotNull List<String> networkResponseHeaders) {
    this.networkResponseHeaders = new ArrayList<>(networkResponseHeaders);
  }

  /**
   * Merges default headers with additional headers, removing duplicates while preserving order.
   *
   * @param defaultHeaders the default headers that are always included
   * @param additionalHeaders additional headers to merge
   */
  private static @NotNull String[] mergeHeaders(
      final @NotNull String[] defaultHeaders, final @NotNull List<String> additionalHeaders) {
    final Set<String> merged = new LinkedHashSet<>();
    merged.addAll(Arrays.asList(defaultHeaders));
    merged.addAll(additionalHeaders);
    return merged.toArray(new String[0]);
  }
}
