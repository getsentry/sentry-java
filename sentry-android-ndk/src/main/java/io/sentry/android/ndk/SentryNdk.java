package io.sentry.android.ndk;

import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.ndk.NativeModuleListLoader;
import io.sentry.ndk.NdkOptions;
import io.sentry.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class SentryNdk {

  private static final @NotNull CountDownLatch loadLibraryLatch = new CountDownLatch(1);

  private SentryNdk() {}

  static {
    new Thread(
            () -> {
              try {
                //noinspection UnstableApiUsage
                io.sentry.ndk.SentryNdk.loadNativeLibraries();
              } catch (Throwable t) {
                // ignored
                // if loadLibrary() fails, the later init() will throw an exception anyway
              } finally {
                loadLibraryLatch.countDown();
              }
            },
            "SentryNdkLoadLibs")
        .start();
  }

  /**
   * Init the NDK integration
   *
   * @param options the SentryAndroidOptions
   */
  public static void init(@NotNull final SentryAndroidOptions options) {
    SentryNdkUtil.addPackage(options.getSdkVersion());
    try {
      if (loadLibraryLatch.await(2000, TimeUnit.MILLISECONDS)) {
        final @NotNull NdkOptions ndkOptions =
            new NdkOptions(
                Objects.requireNonNull(options.getDsn(), "DSN is required for sentry-ndk"),
                options.isDebug(),
                Objects.requireNonNull(
                    options.getOutboxPath(), "outbox path is required for sentry-ndk"),
                options.getRelease(),
                options.getEnvironment(),
                options.getDist(),
                options.getMaxBreadcrumbs(),
                options.getNativeSdkName());

        //noinspection UnstableApiUsage
        io.sentry.ndk.SentryNdk.init(ndkOptions);

        // only add scope sync observer if the scope sync is enabled.
        if (options.isEnableScopeSync()) {
          options.addScopeObserver(new NdkScopeObserver(options));
        }

        options.setDebugImagesLoader(new DebugImagesLoader(options, new NativeModuleListLoader()));
      } else {
        throw new IllegalStateException("Timeout waiting for Sentry NDK library to load");
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Thread interrupted while waiting for NDK libs to be loaded", e);
    }
  }

  /** Closes the NDK integration */
  public static void close() {
    try {
      if (loadLibraryLatch.await(2000, TimeUnit.MILLISECONDS)) {
        //noinspection UnstableApiUsage
        io.sentry.ndk.SentryNdk.close();
      } else {
        throw new IllegalStateException("Timeout waiting for Sentry NDK library to load");
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Thread interrupted while waiting for NDK libs to be loaded", e);
    }
  }
}
