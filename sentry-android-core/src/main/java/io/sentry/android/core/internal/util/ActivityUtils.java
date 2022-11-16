package io.sentry.android.core.internal.util;

import android.app.Activity;
import android.os.Build;
import androidx.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class ActivityUtils {
  public static boolean isActivityValid(@Nullable Activity activity) {
    if (activity == null) {
      return false;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      return !activity.isFinishing() && !activity.isDestroyed();
    } else {
      return !activity.isFinishing();
    }
  }
}
