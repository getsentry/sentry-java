package io.sentry;

import io.sentry.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

@ApiStatus.Internal
public final class TransactionPerformanceCollector {
  private static final long TRANSACTION_COLLECTION_INTERVAL_MILLIS = 100;
  private static final long TRANSACTION_COLLECTION_TIMEOUT_MILLIS = 30000;
  private final @NotNull Object timerLock = new Object();
  private volatile @NotNull Timer timer = new Timer();
  private final @NotNull Map<String, ArrayList<MemoryCollectionData>> memoryMap =
      new ConcurrentHashMap<>();
  private @Nullable IMemoryCollector memoryCollector = null;
  private final @NotNull SentryOptions options;
  private final @NotNull AtomicBoolean isStarted = new AtomicBoolean(false);

  public TransactionPerformanceCollector(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "The options object is required.");
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public void start(final @NotNull ITransaction transaction) {
    // We are putting the TransactionPerformanceCollector in the options, so we want to wait until
    // the options are customized before reading the memory collector
    if (memoryCollector == null) {
      this.memoryCollector = options.getMemoryCollector();
    }
    if (memoryCollector instanceof NoOpMemoryCollector) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "Memory collector is a NoOpMemoryCollector. Memory stats will not be captured during transactions.");
      return;
    }
    if (!memoryMap.containsKey(transaction.getEventId().toString())) {
      memoryMap.put(transaction.getEventId().toString(), new ArrayList<>());
      options
          .getExecutorService()
          .schedule(
              () -> {
                ArrayList<MemoryCollectionData> memoryCollectionData =
                    (ArrayList<MemoryCollectionData>) stop(transaction);
                if (memoryCollectionData != null) {
                  memoryMap.put(transaction.getEventId().toString(), memoryCollectionData);
                }
              },
              TRANSACTION_COLLECTION_TIMEOUT_MILLIS);
    }
    if (!isStarted.getAndSet(true)) {
      synchronized (timerLock) {
        timer.scheduleAtFixedRate(
            new TimerTask() {
              @Override
              public void run() {
                if (memoryCollector != null) {
                  MemoryCollectionData memoryData = memoryCollector.collect();
                  if (memoryData != null) {
                    synchronized (timerLock) {
                      for (ArrayList<MemoryCollectionData> list : memoryMap.values()) {
                        list.add(memoryData);
                      }
                    }
                  }
                }
              }
            },
            0,
            TRANSACTION_COLLECTION_INTERVAL_MILLIS);
      }
    }
  }

  public @Unmodifiable @Nullable List<MemoryCollectionData> stop(
      final @NotNull ITransaction transaction) {
    synchronized (timerLock) {
      List<MemoryCollectionData> memoryData = memoryMap.remove(transaction.getEventId().toString());
      if (memoryMap.isEmpty() && isStarted.getAndSet(false)) {
        timer.cancel();
        timer = new Timer();
      }
      return memoryData;
    }
  }
}
