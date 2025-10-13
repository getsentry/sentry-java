package io.sentry.android.core.internal.util;

import android.os.StrictMode;
import io.sentry.util.runtime.IRuntimeManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class AndroidRuntimeManager implements IRuntimeManager {
  @Override
  public <T> T runWithRelaxedPolicy(final @NotNull IRuntimeManagerCallback<T> toRun) {
    final @NotNull StrictMode.ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
    final @NotNull StrictMode.VmPolicy oldVmPolicy = StrictMode.getVmPolicy();
    StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.LAX);
    StrictMode.setVmPolicy(StrictMode.VmPolicy.LAX);
    final @NotNull T t = toRun.run();
    StrictMode.setThreadPolicy(oldPolicy);
    StrictMode.setVmPolicy(oldVmPolicy);
    return t;
  }
}
