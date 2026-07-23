package io.sentry.android.core;

import android.app.Activity;
import android.net.Uri;
import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import io.sentry.SentryOptions;
import io.sentry.util.LoadClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Launches the androidx photo picker to attach an image to user feedback. All androidx.activity
 * references are isolated in this class, so it must only be loaded after {@link #isAvailable} has
 * returned true.
 */
final class SentryFeedbackPhotoPicker {

  interface OnImagePicked {
    void onImagePicked(@Nullable Uri uri);
  }

  private final @NotNull ActivityResultLauncher<PickVisualMediaRequest> launcher;

  private SentryFeedbackPhotoPicker(
      final @NotNull ActivityResultLauncher<PickVisualMediaRequest> launcher) {
    this.launcher = launcher;
  }

  static boolean isAvailable(
      final @NotNull LoadClass loadClass, final @NotNull SentryOptions options) {
    return loadClass.isClassAvailable("androidx.activity.ComponentActivity", options)
        && loadClass.isClassAvailable(
            "androidx.activity.result.contract.ActivityResultContracts$PickVisualMedia", options);
  }

  static @Nullable SentryFeedbackPhotoPicker register(
      final @NotNull Activity activity, final @NotNull OnImagePicked callback) {
    if (!(activity instanceof ComponentActivity)) {
      return null;
    }
    final @NotNull ActivityResultLauncher<PickVisualMediaRequest> launcher =
        ((ComponentActivity) activity)
            .getActivityResultRegistry()
            .register(
                "sentry_user_feedback_photo_picker",
                new ActivityResultContracts.PickVisualMedia(),
                callback::onImagePicked);
    return new SentryFeedbackPhotoPicker(launcher);
  }

  void launch() {
    launcher.launch(
        new PickVisualMediaRequest.Builder()
            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
            .build());
  }

  void unregister() {
    launcher.unregister();
  }
}
