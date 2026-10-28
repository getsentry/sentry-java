package io.sentry.android.ndk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Build;
import io.sentry.android.core.NdkHandlerStrategy;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.ndk.NativeModuleListLoader;
import io.sentry.ndk.NdkOptions;
import io.sentry.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class SentryNdk {

  private static final @NotNull CountDownLatch loadLibraryLatch = new CountDownLatch(1);

  @SuppressLint("StaticFieldLeak") // the application context from NdkIntegration
  private static @Nullable Context serviceContext;

  private static @Nullable ServiceConnection serviceConnection;

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
    init(null, options);
  }

  /**
   * Init the NDK integration
   *
   * @param context the Android context
   * @param options the SentryAndroidOptions
   */
  public static void init(
      @Nullable final Context context, @NotNull final SentryAndroidOptions options) {
    // no fork/exec fallback, the native crash daemon only runs in the crash service
    if (context == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      throw new IllegalStateException("The NDK crash service needs a context and API 30+");
    }

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

        final int handlerStrategy = options.getNdkHandlerStrategy();
        if (handlerStrategy == NdkHandlerStrategy.SENTRY_HANDLER_STRATEGY_DEFAULT.getValue()) {
          ndkOptions.setNdkHandlerStrategy(
              io.sentry.ndk.NdkHandlerStrategy.SENTRY_HANDLER_STRATEGY_DEFAULT);
        } else if (handlerStrategy
            == NdkHandlerStrategy.SENTRY_HANDLER_STRATEGY_CHAIN_AT_START.getValue()) {
          ndkOptions.setNdkHandlerStrategy(
              io.sentry.ndk.NdkHandlerStrategy.SENTRY_HANDLER_STRATEGY_CHAIN_AT_START);
        }

        final @Nullable Double tracesSampleRate = options.getTracesSampleRate();
        if (tracesSampleRate == null) {
          ndkOptions.setTracesSampleRate(0.0f);
        } else {
          ndkOptions.setTracesSampleRate(tracesSampleRate.floatValue());
        }

        ndkOptions.setEnableAppHangTracking(options.isEnableNdkAppHangTracking());
        ndkOptions.setAppHangTimeoutMillis(options.getNdkAppHangTimeoutIntervalMillis());

        final @Nullable ServiceConnection connection =
            SentryNdkCrashService.start(context, options);
        if (connection == null) {
          throw new IllegalStateException("Failed to start the NDK crash service");
        }
        serviceContext = context;
        serviceConnection = connection;

        try {
          //noinspection UnstableApiUsage
          io.sentry.ndk.SentryNdk.init(ndkOptions);
        } catch (IllegalStateException e) {
          unbindCrashService();
          throw e;
        }

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
    } finally {
      unbindCrashService();
    }
  }

  private static void unbindCrashService() {
    final @Nullable Context context = serviceContext;
    final @Nullable ServiceConnection connection = serviceConnection;
    serviceContext = null;
    serviceConnection = null;
    if (context != null && connection != null) {
      context.unbindService(connection);
    }
  }
}
