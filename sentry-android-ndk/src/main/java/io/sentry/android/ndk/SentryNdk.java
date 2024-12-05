package io.sentry.android.ndk;

import io.sentry.android.core.SentryAndroidOptions;
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
              // On older Android versions, it was necessary to manually call "`System.loadLibrary`
              // on all
              // transitive dependencies before loading [the] main library."
              // The dependencies of `libsentry.so` are currently `lib{c,m,dl,log}.so`.
              // See
              // https://android.googlesource.com/platform/bionic/+/master/android-changes-for-ndk-developers.md#changes-to-library-dependency-resolution
              try {
                System.loadLibrary("log");
                System.loadLibrary("sentry");
                System.loadLibrary("sentry-android");
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

  private static native void initSentryNative(@NotNull final SentryAndroidOptions options);

  private static native void shutdown();

  /**
   * Init the NDK integration
   *
   * @param options the SentryAndroidOptions
   */
  public static void init(@NotNull final SentryAndroidOptions options) {
    SentryNdkUtil.addPackage(options.getSdkVersion());
    try {
      if (loadLibraryLatch.await(2000, TimeUnit.MILLISECONDS)) {
        initSentryNative(options);

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
        shutdown();
      } else {
        throw new IllegalStateException("Timeout waiting for Sentry NDK library to load");
      }
    } catch (InterruptedException e) {
      throw new IllegalStateException(
          "Thread interrupted while waiting for NDK libs to be loaded", e);
    }
  }
}
