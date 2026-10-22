package io.sentry.android.ndk;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import io.sentry.DateUtils;
import io.sentry.ILogger;
import io.sentry.SentryEnvelope;
import io.sentry.SentryEnvelopeItem;
import io.sentry.SentryEvent;
import io.sentry.SentryItemType;
import io.sentry.SentryLevel;
import io.sentry.android.core.AndroidLogger;
import io.sentry.android.core.SentryAndroidOptions;
import io.sentry.android.core.TombstoneIntegration;
import io.sentry.android.core.cache.AndroidEnvelopeCache;
import io.sentry.android.core.internal.tombstone.TombstoneParser;
import io.sentry.ndk.SentryNdk.CrashDaemon;
import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

@ApiStatus.Internal
public final class SentryNdkCrashService extends Service {

  private static final @NotNull String BINDER_DESCRIPTOR = "io.sentry.android.ndk.CrashService";
  private static final int TRANSACTION_CONFIGURE = IBinder.FIRST_CALL_TRANSACTION;

  private static final @NotNull String MERGED_SUFFIX = ".merged";
  private static final long POLL_INTERVAL_MILLIS = 250;
  private static final long MATCH_TOLERANCE_MILLIS = 30000;

  private final @NotNull Object lock = new Object();

  private @Nullable Thread daemonWorker;
  private @Nullable File nativeDatabaseDir;
  private @Nullable String nativeLibraryDir;
  private @Nullable String shmPath;
  private int notifyReadFd = -1;
  private int readyWriteFd = -1;
  private int messageReadFd = -1;
  private int appPid = -1;
  private long appTid = 0;
  private long shutdownTimeoutMillis = 0;
  private @NotNull List<String> inAppIncludes = new ArrayList<>();
  private @NotNull List<String> inAppExcludes = new ArrayList<>();
  private boolean tombstoneEnabled = false;
  private boolean debug = false;
  private final @NotNull IBinder binder =
      new Binder() {
        @Override
        protected boolean onTransact(
            final int code,
            final @NotNull Parcel data,
            final @Nullable Parcel reply,
            final int flags)
            throws RemoteException {
          if (code != TRANSACTION_CONFIGURE) {
            return super.onTransact(code, data, reply, flags);
          }

          data.enforceInterface(BINDER_DESCRIPTOR);
          final @Nullable String nativeDatabasePath = data.readString();
          final @Nullable String nativeLibraryDir = data.readString();
          final @Nullable String shmPath = data.readString();
          final int appPid = data.readInt();
          final long appTid = data.readLong();
          final boolean tombstoneEnabled = data.readInt() == 1;
          final boolean debug = data.readInt() == 1;
          final long shutdownTimeoutMillis = data.readLong();
          final @Nullable ArrayList<String> includes = data.createStringArrayList();
          final @Nullable ArrayList<String> excludes = data.createStringArrayList();
          final @Nullable ParcelFileDescriptor notifyRead = data.readFileDescriptor();
          final @Nullable ParcelFileDescriptor readyWrite = data.readFileDescriptor();
          final @Nullable ParcelFileDescriptor messageRead = data.readFileDescriptor();

          final boolean configured =
              configureDaemon(
                  nativeDatabasePath,
                  nativeLibraryDir,
                  shmPath,
                  appPid,
                  appTid,
                  tombstoneEnabled,
                  debug,
                  shutdownTimeoutMillis,
                  includes,
                  excludes,
                  notifyRead,
                  readyWrite,
                  messageRead);
          if (reply != null) {
            reply.writeNoException();
            reply.writeInt(configured ? 1 : 0);
          }
          return true;
        }
      };

  static @Nullable ServiceConnection start(
      final @NotNull Context context, final @NotNull SentryAndroidOptions options) {
    final @Nullable String outboxPath = options.getOutboxPath();
    if (outboxPath == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Outbox path is null, not starting NDK crash service.");
      return null;
    }

    final @Nullable File cacheDir = new File(outboxPath).getParentFile();
    if (cacheDir == null) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Cache dir is null, not starting NDK crash service.");
      return null;
    }

    final File nativeDatabaseDir = new File(cacheDir, ".sentry-native");
    if (!nativeDatabaseDir.exists() && !nativeDatabaseDir.mkdirs()) {
      options.getLogger().log(SentryLevel.DEBUG, "Failed to create NDK database directory.");
      return null;
    }

    final long ipcId = System.nanoTime();
    final File shmFile =
        new File(nativeDatabaseDir, ".sentry-shm-service-" + Long.toHexString(ipcId));

    ParcelFileDescriptor notifyRead = null;
    ParcelFileDescriptor notifyWrite = null;
    ParcelFileDescriptor readyRead = null;
    ParcelFileDescriptor readyWrite = null;
    ParcelFileDescriptor messageWrite = null;
    ParcelFileDescriptor messageRead = null;
    try {
      final ParcelFileDescriptor[] notifyPipe = ParcelFileDescriptor.createPipe();
      final ParcelFileDescriptor[] readyPipe = ParcelFileDescriptor.createPipe();
      notifyRead = notifyPipe[0];
      notifyWrite = notifyPipe[1];
      readyRead = readyPipe[0];
      readyWrite = readyPipe[1];
      final ParcelFileDescriptor[] messageSocket = ParcelFileDescriptor.createSocketPair();
      messageWrite = messageSocket[0];
      messageRead = messageSocket[1];

      final Intent intent = new Intent(context, SentryNdkCrashService.class);
      final @Nullable ServiceConnection connection =
          configureService(
              context,
              intent,
              options.getLogger(),
              nativeDatabaseDir.getPath(),
              context.getApplicationInfo().nativeLibraryDir,
              shmFile.getPath(),
              Process.myPid(),
              ipcId,
              options.isTombstoneEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
              options.isDebug(),
              options.getShutdownTimeoutMillis(),
              new ArrayList<>(options.getInAppIncludes()),
              new ArrayList<>(options.getInAppExcludes()),
              notifyRead,
              readyWrite,
              messageRead);
      if (connection == null) {
        options.getLogger().log(SentryLevel.DEBUG, "NDK crash service did not configure.");
        return null;
      }

      io.sentry.ndk.SentryNdk.initCrashDaemon(
          shmFile.getPath(), notifyWrite.detachFd(), readyRead.detachFd(), messageWrite.detachFd());
      return connection;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      options.getLogger().log(SentryLevel.DEBUG, e, "Failed to start NDK crash service.");
      return null;
    } catch (IOException | SecurityException e) {
      options.getLogger().log(SentryLevel.DEBUG, e, "Failed to start NDK crash service.");
      return null;
    } finally {
      closeQuietly(notifyRead);
      closeQuietly(notifyWrite);
      closeQuietly(readyRead);
      closeQuietly(readyWrite);
      closeQuietly(messageWrite);
      closeQuietly(messageRead);
    }
  }

  @Override
  public @Nullable IBinder onBind(final @NotNull Intent intent) {
    return binder;
  }

  private static void closeQuietly(final @Nullable Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException ignored) {
        // ignored
      }
    }
  }

  private static @Nullable ServiceConnection configureService(
      final @NotNull Context context,
      final @NotNull Intent intent,
      final @NotNull ILogger logger,
      final @NotNull String nativeDatabasePath,
      final @Nullable String nativeLibraryDir,
      final @NotNull String shmPath,
      final int appPid,
      final long appTid,
      final boolean tombstoneEnabled,
      final boolean debug,
      final long shutdownTimeoutMillis,
      final @NotNull ArrayList<String> includes,
      final @NotNull ArrayList<String> excludes,
      final @NotNull ParcelFileDescriptor notifyRead,
      final @NotNull ParcelFileDescriptor readyWrite,
      final @NotNull ParcelFileDescriptor messageRead)
      throws InterruptedException {
    final CountDownLatch latch = new CountDownLatch(1);
    final boolean[] configured = new boolean[] {false};
    final boolean[] bound = new boolean[] {false};

    final ServiceConnection connection =
        new ServiceConnection() {
          @Override
          public void onServiceConnected(
              final @NotNull ComponentName name, final @NotNull IBinder service) {
            configured[0] =
                transactConfigure(
                    service,
                    nativeDatabasePath,
                    nativeLibraryDir,
                    shmPath,
                    appPid,
                    appTid,
                    tombstoneEnabled,
                    debug,
                    shutdownTimeoutMillis,
                    includes,
                    excludes,
                    notifyRead,
                    readyWrite,
                    messageRead);
            latch.countDown();
          }

          @Override
          public void onServiceDisconnected(final @NotNull ComponentName name) {
            logger.log(SentryLevel.WARNING, "NDK crash service disconnected.");
            latch.countDown();
          }

          @Override
          public void onBindingDied(final @NotNull ComponentName name) {
            logger.log(SentryLevel.WARNING, "NDK crash service binding died.");
            latch.countDown();
          }
        };

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      // deliver callbacks on the binder thread, the caller blocks on the latch
      bound[0] = context.bindService(intent, Context.BIND_AUTO_CREATE, Runnable::run, connection);
    } else {
      bound[0] = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }
    if (!bound[0]) {
      return null;
    }
    boolean ready = false;
    try {
      ready = latch.await(10000, TimeUnit.MILLISECONDS) && configured[0];
      // stay bound, so the service process keeps the app's importance and is not frozen
      return ready ? connection : null;
    } finally {
      if (!ready) {
        context.unbindService(connection);
      }
    }
  }

  private static boolean transactConfigure(
      final @NotNull IBinder service,
      final @NotNull String nativeDatabasePath,
      final @Nullable String nativeLibraryDir,
      final @NotNull String shmPath,
      final int appPid,
      final long appTid,
      final boolean tombstoneEnabled,
      final boolean debug,
      final long shutdownTimeoutMillis,
      final @NotNull ArrayList<String> includes,
      final @NotNull ArrayList<String> excludes,
      final @NotNull ParcelFileDescriptor notifyRead,
      final @NotNull ParcelFileDescriptor readyWrite,
      final @NotNull ParcelFileDescriptor messageRead) {
    final Parcel data = Parcel.obtain();
    final Parcel reply = Parcel.obtain();
    try {
      data.writeInterfaceToken(BINDER_DESCRIPTOR);
      data.writeString(nativeDatabasePath);
      data.writeString(nativeLibraryDir);
      data.writeString(shmPath);
      data.writeInt(appPid);
      data.writeLong(appTid);
      data.writeInt(tombstoneEnabled ? 1 : 0);
      data.writeInt(debug ? 1 : 0);
      data.writeLong(shutdownTimeoutMillis);
      data.writeStringList(includes);
      data.writeStringList(excludes);
      data.writeFileDescriptor(notifyRead.getFileDescriptor());
      data.writeFileDescriptor(readyWrite.getFileDescriptor());
      data.writeFileDescriptor(messageRead.getFileDescriptor());
      if (!service.transact(TRANSACTION_CONFIGURE, data, reply, 0)) {
        return false;
      }
      reply.readException();
      return reply.readInt() == 1;
    } catch (RemoteException | RuntimeException ignored) {
      return false;
    } finally {
      data.recycle();
      reply.recycle();
    }
  }

  private boolean configureDaemon(
      final @Nullable String nativeDatabasePath,
      final @Nullable String nativeLibraryDir,
      final @Nullable String shmPath,
      final int appPid,
      final long appTid,
      final boolean tombstoneEnabled,
      final boolean debug,
      final long shutdownTimeoutMillis,
      final @Nullable ArrayList<String> includes,
      final @Nullable ArrayList<String> excludes,
      final @Nullable ParcelFileDescriptor notifyRead,
      final @Nullable ParcelFileDescriptor readyWrite,
      final @Nullable ParcelFileDescriptor messageRead) {
    if (nativeDatabasePath == null
        || shmPath == null
        || notifyRead == null
        || readyWrite == null
        || messageRead == null
        || appPid < 0) {
      return false;
    }

    synchronized (lock) {
      nativeDatabaseDir = new File(nativeDatabasePath);
      this.nativeLibraryDir = nativeLibraryDir;
      this.shmPath = shmPath;
      this.appPid = appPid;
      this.appTid = appTid;
      this.tombstoneEnabled = tombstoneEnabled;
      this.debug = debug;
      this.shutdownTimeoutMillis = shutdownTimeoutMillis;
      inAppIncludes = includes != null ? includes : new ArrayList<>();
      inAppExcludes = excludes != null ? excludes : new ArrayList<>();
      notifyReadFd = notifyRead.detachFd();
      readyWriteFd = readyWrite.detachFd();
      messageReadFd = messageRead.detachFd();
      startWorkersLocked();
    }
    return true;
  }

  private void startWorkersLocked() {
    if (daemonWorker == null && isDaemonConfigured()) {
      final @Nullable String path = shmPath;
      final int pid = appPid;
      daemonWorker = new Thread(() -> runDaemonWorker(path, pid), "SentryNdkCrashService");
      daemonWorker.start();
    }
  }

  private void runDaemonWorker(final @Nullable String path, final int pid) {
    final SentryAndroidOptions options = new SentryAndroidOptions();
    options.setLogger(new AndroidLogger("SentryNdkCrash"));
    options.setDebug(debug);

    try {
      if (path == null || notifyReadFd < 0 || readyWriteFd < 0 || messageReadFd < 0 || pid < 0) {
        return;
      }

      // keeps going after onDestroy, a crashed app's binding goes away mid-crash
      try (final @Nullable CrashDaemon daemon =
              io.sentry.ndk.SentryNdk.runCrashDaemon(
                  pid, appTid, notifyReadFd, readyWriteFd, messageReadFd, path)) {
        if (daemon == null) {
          return;
        }
        final @Nullable String envelopePath = daemon.getEnvelopePath();
        if (envelopePath == null) {
          return;
        }

        final long deadlineMillis = System.currentTimeMillis() + shutdownTimeoutMillis;
        final File envelope = new File(envelopePath);
        final @Nullable MergedEnvelope mergedEnvelope =
            tombstoneEnabled ? waitForMergedEnvelope(options, envelope, deadlineMillis, pid) : null;
        if (tombstoneEnabled && mergedEnvelope == null) {
          options
              .getLogger()
              .log(SentryLevel.DEBUG, "Sending NDK crash without tombstone enrichment.");
        }

        final long remainingMillis = Math.max(0, deadlineMillis - System.currentTimeMillis());
        if (!daemon.sendEnvelope(
            mergedEnvelope != null ? mergedEnvelope.file.getPath() : envelope.getPath(),
            remainingMillis)) {
          options.getLogger().log(SentryLevel.DEBUG, "Failed to send NDK crash envelope.");
        } else if (mergedEnvelope != null) {
          writeLastTombstoneMarker(envelope, mergedEnvelope.tombstoneTimestamp);
        }
      }
    } catch (InterruptedException | IOException e) {
      options.getLogger().log(SentryLevel.DEBUG, e, "Error running NDK crash service.");
    } finally {
      synchronized (lock) {
        daemonWorker = null;
        notifyReadFd = -1;
        readyWriteFd = -1;
        messageReadFd = -1;
        appPid = -1;
        appTid = 0;
        shmPath = null;
      }
    }
  }

  private boolean isDaemonConfigured() {
    return shmPath != null
        && notifyReadFd >= 0
        && readyWriteFd >= 0
        && messageReadFd >= 0
        && appPid >= 0;
  }

  private @Nullable MergedEnvelope waitForMergedEnvelope(
      final @NotNull SentryAndroidOptions options,
      final @NotNull File envelope,
      final long deadlineMillis,
      final int pid)
      throws InterruptedException {
    while (true) {
      final @Nullable MergedEnvelope mergedEnvelope = processEnvelope(options, envelope, pid);
      if (mergedEnvelope != null) {
        return mergedEnvelope;
      }

      final long remainingMillis = deadlineMillis - System.currentTimeMillis();
      if (remainingMillis <= 0) {
        return null;
      }
      Thread.sleep(Math.min(POLL_INTERVAL_MILLIS, remainingMillis));
    }
  }

  @SuppressLint("NewApi")
  private @Nullable MergedEnvelope processEnvelope(
      final @NotNull SentryAndroidOptions options, final @NotNull File file, final int pid) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
      return null;
    }

    final @Nullable NativeEnvelope nativeEnvelope = readNativeEnvelope(options, file);
    if (nativeEnvelope == null) {
      return null;
    }

    final @Nullable Date nativeTimestamp = nativeEnvelope.event.getTimestamp();
    if (nativeTimestamp == null) {
      return null;
    }

    final @Nullable ApplicationExitInfo exitInfo =
        findMatchingExitInfo(options, nativeTimestamp, pid);
    if (exitInfo == null) {
      return null;
    }

    try (final @Nullable InputStream tombstoneInputStream = exitInfo.getTraceInputStream()) {
      if (tombstoneInputStream == null) {
        return null;
      }

      final SentryEvent tombstoneEvent;
      try (final TombstoneParser parser =
          new TombstoneParser(
              tombstoneInputStream, inAppIncludes, inAppExcludes, nativeLibraryDir)) {
        tombstoneEvent = parser.parse();
      }
      tombstoneEvent.setTimestamp(DateUtils.getDateTime(exitInfo.getTimestamp()));

      TombstoneIntegration.TombstonePolicy.mergeNativeCrashes(nativeEnvelope.event, tombstoneEvent);
      final File mergedEnvelope = writeMergedEnvelope(options, file, nativeEnvelope);
      return new MergedEnvelope(mergedEnvelope, exitInfo.getTimestamp());
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.DEBUG, e, "Failed to merge NDK tombstone envelope.");
    }
    return null;
  }

  @VisibleForTesting
  @SuppressLint("NewApi")
  @Nullable
  ApplicationExitInfo findMatchingExitInfo(
      final @NotNull SentryAndroidOptions options,
      final @NotNull Date nativeTimestamp,
      final int pid) {
    final @Nullable ActivityManager activityManager =
        (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
    if (activityManager == null) {
      return null;
    }

    final List<ApplicationExitInfo> exitInfos =
        activityManager.getHistoricalProcessExitReasons(null, 0, 10);
    ApplicationExitInfo bestMatch = null;
    long bestDiff = Long.MAX_VALUE;
    for (ApplicationExitInfo exitInfo : exitInfos) {
      if (exitInfo.getReason() != ApplicationExitInfo.REASON_CRASH_NATIVE
          || exitInfo.getPid() != pid) {
        continue;
      }
      final long diff = Math.abs(exitInfo.getTimestamp() - nativeTimestamp.getTime());
      if (diff < bestDiff) {
        bestDiff = diff;
        bestMatch = exitInfo;
      }
    }

    if (bestMatch != null && bestDiff <= MATCH_TOLERANCE_MILLIS) {
      options
          .getLogger()
          .log(SentryLevel.DEBUG, "Matched NDK tombstone with diff %d ms.", bestDiff);
      return bestMatch;
    }
    return null;
  }

  private @Nullable NativeEnvelope readNativeEnvelope(
      final @NotNull SentryAndroidOptions options, final @NotNull File file) {
    try (final InputStream stream = new BufferedInputStream(new FileInputStream(file))) {
      final @Nullable SentryEnvelope envelope = options.getEnvelopeReader().read(stream);
      if (envelope == null) {
        return null;
      }
      for (SentryEnvelopeItem item : envelope.getItems()) {
        if (item.getHeader().getType() != SentryItemType.Event) {
          continue;
        }
        final @Nullable SentryEvent event = item.getEvent(options.getSerializer());
        if (event != null && "native".equals(event.getPlatform())) {
          return new NativeEnvelope(envelope, event);
        }
      }
    } catch (Exception e) {
      options.getLogger().log(SentryLevel.DEBUG, e, "Failed to read NDK envelope.");
    }
    return null;
  }

  private @NotNull File writeMergedEnvelope(
      final @NotNull SentryAndroidOptions options,
      final @NotNull File sourceFile,
      final @NotNull NativeEnvelope nativeEnvelope)
      throws Exception {
    final List<SentryEnvelopeItem> items = new ArrayList<>();
    items.add(SentryEnvelopeItem.fromEvent(options.getSerializer(), nativeEnvelope.event));
    for (SentryEnvelopeItem item : nativeEnvelope.envelope.getItems()) {
      if (item.getHeader().getType() != SentryItemType.Event) {
        items.add(item);
      }
    }

    final SentryEnvelope mergedEnvelope =
        new SentryEnvelope(nativeEnvelope.envelope.getHeader(), items);
    final File mergedFile = new File(sourceFile.getPath() + MERGED_SUFFIX);
    final File tmpFile = new File(mergedFile.getPath() + ".tmp");
    try (final OutputStream outputStream = new FileOutputStream(tmpFile)) {
      options.getSerializer().serialize(mergedEnvelope, outputStream);
    }
    if (!tmpFile.renameTo(mergedFile)) {
      throw new IllegalStateException("Failed to publish merged envelope");
    }
    options.getLogger().log(SentryLevel.DEBUG, "Wrote merged NDK tombstone envelope.");
    return mergedFile;
  }

  private void writeLastTombstoneMarker(final @NotNull File envelopeFile, final long timestamp)
      throws IOException {
    final @Nullable File runDir = envelopeFile.getParentFile();
    final @Nullable File nativeDir = runDir != null ? runDir.getParentFile() : null;
    final @Nullable File cacheDir = nativeDir != null ? nativeDir.getParentFile() : null;
    if (cacheDir == null) {
      return;
    }
    final File marker = new File(cacheDir, AndroidEnvelopeCache.LAST_TOMBSTONE_REPORT);
    try (final OutputStream outputStream = new FileOutputStream(marker)) {
      outputStream.write(String.valueOf(timestamp).getBytes("UTF-8"));
    }
  }

  private static final class NativeEnvelope {
    private final @NotNull SentryEnvelope envelope;
    private final @NotNull SentryEvent event;

    private NativeEnvelope(
        final @NotNull SentryEnvelope envelope, final @NotNull SentryEvent event) {
      this.envelope = envelope;
      this.event = event;
    }
  }

  private static final class MergedEnvelope {
    private final @NotNull File file;
    private final long tombstoneTimestamp;

    private MergedEnvelope(final @NotNull File file, final long tombstoneTimestamp) {
      this.file = file;
      this.tombstoneTimestamp = tombstoneTimestamp;
    }
  }
}
