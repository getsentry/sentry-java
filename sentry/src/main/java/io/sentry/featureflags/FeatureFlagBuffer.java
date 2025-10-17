package io.sentry.featureflags;

import io.sentry.ISentryLifecycleToken;
import io.sentry.ScopeType;
import io.sentry.SentryOptions;
import io.sentry.protocol.FeatureFlag;
import io.sentry.protocol.FeatureFlags;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class FeatureFlagBuffer implements IFeatureFlagBuffer {

  private volatile @NotNull CopyOnWriteArrayList<FeatureFlagEntry> flags;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  private int maxSize;

  private FeatureFlagBuffer(int maxSize) {
    this.maxSize = maxSize;
    this.flags = new CopyOnWriteArrayList<>();
  }

  private FeatureFlagBuffer(
      int maxSize, final @NotNull CopyOnWriteArrayList<FeatureFlagEntry> flags) {
    this.maxSize = maxSize;
    this.flags = flags;
  }

  private FeatureFlagBuffer(@NotNull FeatureFlagBuffer other) {
    this.maxSize = other.maxSize;
    this.flags = new CopyOnWriteArrayList<>(other.flags);
  }

  @Override
  public void add(@NotNull String flag, boolean result) {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final int size = flags.size();
      final @NotNull ArrayList<FeatureFlagEntry> tmpList = new ArrayList<>(size + 1);
      for (FeatureFlagEntry entry : flags) {
        if (!entry.flag.equals(flag)) {
          tmpList.add(entry);
        }
      }
      tmpList.add(new FeatureFlagEntry(flag, result, System.nanoTime()));

      if (tmpList.size() > maxSize) {
        tmpList.remove(0);
      }

      flags = new CopyOnWriteArrayList<>(tmpList);
    }
  }

  @Override
  public @NotNull FeatureFlags getFeatureFlags() {
    List<FeatureFlag> featureFlags = new ArrayList<>();
    for (FeatureFlagEntry entry : flags) {
      featureFlags.add(entry.toFeatureFlag());
    }
    return new FeatureFlags(featureFlags);
  }

  @Override
  public IFeatureFlagBuffer clone() {
    return new FeatureFlagBuffer(this);
  }

  public static @NotNull IFeatureFlagBuffer create(final @NotNull SentryOptions options) {
    final int maxFeatureFlags = options.getMaxFeatureFlags();
    if (maxFeatureFlags > 0) {
      return new FeatureFlagBuffer(maxFeatureFlags);
    } else {
      return NoOpFeatureFlagBuffer.getInstance();
    }
  }

  public static @NotNull IFeatureFlagBuffer merged(
      final @NotNull SentryOptions options,
      final @Nullable IFeatureFlagBuffer globalBuffer,
      final @Nullable IFeatureFlagBuffer isolationBuffer,
      final @Nullable IFeatureFlagBuffer currentBuffer) {
    final int maxSize = options.getMaxFeatureFlags();
    if (maxSize <= 0) {
      return NoOpFeatureFlagBuffer.getInstance();
    }

    return merged(
        maxSize,
        globalBuffer instanceof FeatureFlagBuffer ? (FeatureFlagBuffer) globalBuffer : null,
        isolationBuffer instanceof FeatureFlagBuffer ? (FeatureFlagBuffer) isolationBuffer : null,
        currentBuffer instanceof FeatureFlagBuffer ? (FeatureFlagBuffer) currentBuffer : null);
  }

  private static @NotNull IFeatureFlagBuffer merged(
      final int maxSize,
      final @Nullable FeatureFlagBuffer globalBuffer,
      final @Nullable FeatureFlagBuffer isolationBuffer,
      final @Nullable FeatureFlagBuffer currentBuffer) {

    // Capture references to avoid inconsistencies from concurrent modifications
    final @Nullable CopyOnWriteArrayList<FeatureFlagEntry> globalFlags =
        globalBuffer == null ? null : globalBuffer.flags;
    final @Nullable CopyOnWriteArrayList<FeatureFlagEntry> isolationFlags =
        isolationBuffer == null ? null : isolationBuffer.flags;
    final @Nullable CopyOnWriteArrayList<FeatureFlagEntry> currentFlags =
        currentBuffer == null ? null : currentBuffer.flags;

    final int globalSize = globalFlags == null ? 0 : globalFlags.size();
    final int isolationSize = isolationFlags == null ? 0 : isolationFlags.size();
    final int currentSize = currentFlags == null ? 0 : currentFlags.size();

    // Early exit if all buffers are empty
    if (globalSize == 0 && isolationSize == 0 && currentSize == 0) {
      return NoOpFeatureFlagBuffer.getInstance();
    }

    int globalIndex = globalSize - 1;
    int isolationIndex = isolationSize - 1;
    int currentIndex = currentSize - 1;

    final @NotNull java.util.Map<String, FeatureFlagEntry> uniqueFlags =
        new java.util.LinkedHashMap<>(maxSize);

    // check if there is still room and remaining items to check
    while (uniqueFlags.size() < maxSize
        && (globalIndex >= 0 || isolationIndex >= 0 || currentIndex >= 0)) {
      final FeatureFlagEntry globalEntry =
          (globalFlags != null && globalIndex >= 0) ? globalFlags.get(globalIndex) : null;
      final FeatureFlagEntry isolationEntry =
          (isolationFlags != null && isolationIndex >= 0)
              ? isolationFlags.get(isolationIndex)
              : null;
      final FeatureFlagEntry currentEntry =
          (currentFlags != null && currentIndex >= 0) ? currentFlags.get(currentIndex) : null;

      @Nullable FeatureFlagEntry entryToAdd = null;
      @Nullable ScopeType selectedBuffer = null;

      // choose newest entry across all buffers
      if (globalEntry != null && (entryToAdd == null || globalEntry.nanos > entryToAdd.nanos)) {
        entryToAdd = globalEntry;
        selectedBuffer = ScopeType.GLOBAL;
      }
      if (isolationEntry != null
          && (entryToAdd == null || isolationEntry.nanos > entryToAdd.nanos)) {
        entryToAdd = isolationEntry;
        selectedBuffer = ScopeType.ISOLATION;
      }
      if (currentEntry != null && (entryToAdd == null || currentEntry.nanos > entryToAdd.nanos)) {
        entryToAdd = currentEntry;
        selectedBuffer = ScopeType.CURRENT;
      }

      if (entryToAdd != null) {
        // no need to update existing entries since we already have the latest
        if (!uniqueFlags.containsKey(entryToAdd.flag)) {
          uniqueFlags.put(entryToAdd.flag, entryToAdd);
        }

        // decrement only index of buffer that was selected
        if (ScopeType.CURRENT.equals(selectedBuffer)) {
          currentIndex--;
        } else if (ScopeType.ISOLATION.equals(selectedBuffer)) {
          isolationIndex--;
        } else if (ScopeType.GLOBAL.equals(selectedBuffer)) {
          globalIndex--;
        }
      } else {
        // no need to look any further since lists are sorted and we could not find any newer
        // entries anymore
        break;
      }
    }

    // Convert to list in reverse order (oldest first, newest last)
    final @NotNull List<FeatureFlagEntry> resultList = new ArrayList<>(uniqueFlags.values());
    Collections.reverse(resultList);
    return new FeatureFlagBuffer(maxSize, new CopyOnWriteArrayList<>(resultList));
  }

  private static class FeatureFlagEntry {

    private final @NotNull String flag;
    private final boolean result;

    @SuppressWarnings("UnusedVariable")
    @NotNull
    private final Long nanos;

    public FeatureFlagEntry(
        final @NotNull String flag, final boolean result, final @NotNull Long nanos) {
      this.flag = flag;
      this.result = result;
      this.nanos = nanos;
    }

    public @NotNull FeatureFlag toFeatureFlag() {
      return new FeatureFlag(flag, result);
    }
  }
}
