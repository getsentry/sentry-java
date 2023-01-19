package io.sentry;

import io.sentry.util.Objects;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class TransactionPerformanceCollector {
  private static final long TRANSACTION_COLLECTION_INTERVAL_MILLIS = 100;
  private static final long TRANSACTION_COLLECTION_TIMEOUT_MILLIS = 30000;
  private final @NotNull Object timerLock = new Object();
  private volatile @Nullable Timer timer = null;
  private final @NotNull Map<String, PerformanceCollectionData> performanceDataMap =
      new ConcurrentHashMap<>();
  private @Nullable IMemoryCollector memoryCollector = null;
  private @Nullable ICpuCollector cpuCollector = null;
  private final @NotNull SentryOptions options;
  private final @NotNull AtomicBoolean isStarted = new AtomicBoolean(false);

  public TransactionPerformanceCollector(final @NotNull SentryOptions options) {
    this.options = Objects.requireNonNull(options, "The options object is required.");
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  public void start(final @NotNull ITransaction transaction) {
    // We are putting the TransactionPerformanceCollector in the options, so we want to wait until
    // the options are customized before reading the collectors
    if (memoryCollector == null) {
      this.memoryCollector = options.getMemoryCollector();
    }
    if (cpuCollector == null) {
      this.cpuCollector = options.getCpuCollector();
    }
    boolean isMemoryCollectorNoOp = memoryCollector instanceof NoOpMemoryCollector;
    boolean isCpuCollectorNoOp = cpuCollector instanceof NoOpCpuCollector;

    if (isMemoryCollectorNoOp) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "Memory collector is a NoOpCollector. Memory stats will not be captured during transactions.");
    }
    if (isCpuCollectorNoOp) {
      options
          .getLogger()
          .log(
              SentryLevel.INFO,
              "Cpu collector is a NoOpCollector. Cpu stats will not be captured during transactions.");
    }
    if (isMemoryCollectorNoOp && isCpuCollectorNoOp) {
      return;
    }

    if (!performanceDataMap.containsKey(transaction.getEventId().toString())) {
      performanceDataMap.put(transaction.getEventId().toString(), new PerformanceCollectionData());
      options
          .getExecutorService()
          .schedule(
              () -> {
                PerformanceCollectionData data = stop(transaction);
                if (data != null) {
                  performanceDataMap.put(transaction.getEventId().toString(), data);
                }
              },
              TRANSACTION_COLLECTION_TIMEOUT_MILLIS);
    }
    if (!isStarted.getAndSet(true)) {
      synchronized (timerLock) {
        cpuCollector.setup();
        if (timer == null) {
          timer = new Timer(true);
        }
        timer.scheduleAtFixedRate(
            new TimerTask() {
              @Override
              public void run() {
                MemoryCollectionData memoryData = null;
                if (memoryCollector != null) {
                  memoryData = memoryCollector.collect();
                }
                CpuCollectionData cpuData = null;
                if (cpuCollector != null) {
                  cpuData = cpuCollector.collect();
                }
                synchronized (timerLock) {
                  for (PerformanceCollectionData data : performanceDataMap.values()) {
                    data.addData(memoryData, cpuData);
                  }
                }
              }
            },
            TRANSACTION_COLLECTION_INTERVAL_MILLIS,
            TRANSACTION_COLLECTION_INTERVAL_MILLIS);
      }
    }
  }

  public @Nullable PerformanceCollectionData stop(final @NotNull ITransaction transaction) {
    synchronized (timerLock) {
      PerformanceCollectionData memoryData =
          performanceDataMap.remove(transaction.getEventId().toString());
      if (performanceDataMap.isEmpty() && isStarted.getAndSet(false)) {
        if (timer != null) {
          timer.cancel();
          timer = null;
        }
      }
      return memoryData;
    }
  }
}
