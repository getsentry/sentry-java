package io.sentry.android.ndk;

import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.ndk.NativeModuleListLoader;
import io.sentry.ndk.NdkOptions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryNdk {

  private SentryNdk() {}

  /**
   * Init the NDK integration
   *
   * @param options the SentryAndroidOptions
   */
  public static void init(@NotNull final SentryAndroidOptions options) {
    SentryNdkUtil.addPackage(options.getSdkVersion());

    final @NotNull NdkOptions ndkOptions =
        new NdkOptions(
            options.getDsn(),
            options.isDebug(),
            options.getOutboxPath(),
            options.getRelease(),
            options.getEnvironment(),
            options.getDist(),
            options.getMaxBreadcrumbs(),
            options.getNativeSdkName());
    io.sentry.ndk.SentryNdk.init(ndkOptions);

    // only add scope sync observer if the scope sync is enabled.
    if (options.isEnableScopeSync()) {
      options.addScopeObserver(new NdkScopeObserver(options));
    }

    options.setDebugImagesLoader(new DebugImagesLoader(options, new NativeModuleListLoader()));
  }

  /** Closes the NDK integration */
  public static void close() {
    io.sentry.ndk.SentryNdk.close();
  }
}
