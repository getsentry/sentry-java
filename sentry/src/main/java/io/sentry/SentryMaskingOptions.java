package io.sentry;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for masking configuration used by both Session Replay and Screenshot features.
 * Contains common settings for which view classes should be masked or unmasked.
 */
public abstract class SentryMaskingOptions {

  public static final String TEXT_VIEW_CLASS_NAME = "android.widget.TextView";
  public static final String IMAGE_VIEW_CLASS_NAME = "android.widget.ImageView";
  public static final String WEB_VIEW_CLASS_NAME = "android.webkit.WebView";
  public static final String VIDEO_VIEW_CLASS_NAME = "android.widget.VideoView";
  public static final String ANDROIDX_MEDIA_VIEW_CLASS_NAME = "androidx.media3.ui.PlayerView";
  public static final String EXOPLAYER_CLASS_NAME = "com.google.android.exoplayer2.ui.PlayerView";
  public static final String EXOPLAYER_STYLED_CLASS_NAME =
      "com.google.android.exoplayer2.ui.StyledPlayerView";

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

  public @Nullable String getMaskViewContainerClass() {
    return maskViewContainerClass;
  }

  public void setMaskViewContainerClass(@NotNull String containerClass) {
    addMaskViewClass(containerClass);
    maskViewContainerClass = containerClass;
  }

  public @Nullable String getUnmaskViewContainerClass() {
    return unmaskViewContainerClass;
  }

  public void setUnmaskViewContainerClass(@NotNull String containerClass) {
    unmaskViewContainerClass = containerClass;
  }
}
