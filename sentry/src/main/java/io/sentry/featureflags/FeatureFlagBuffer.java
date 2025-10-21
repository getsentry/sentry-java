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
  public void add(final @Nullable String flag, final @Nullable Boolean result) {
    if (flag == null || result == null) {
      return;
    }
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final int size = flags.size();
      for (int i = 0; i < size; i++) {
        if (flags.get(i).equals(flag)) {
          flags.remove(i);
          break;
        }
      }
      flags.add(new FeatureFlagEntry(flag, result, System.nanoTime()));

      if (flags.size() > maxSize) {
        flags.remove(0);
      }
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

  /**
   * Iterates all incoming buffers from the end, always taking the latest item across all buffers,
   * until maxSize has been reached or no more items are available.
   *
   * <p>If a duplicate is found we skip it since we're iterating in reverse order and we already
   * have the latest entry.
   *
   * @param maxSize max number of feature flags
   * @param globalBuffer buffer from global scope
   * @param isolationBuffer buffer from isolation scope
   * @param currentBuffer buffer from current scope
   * @return merged buffer containing at most maxSize latest items from incoming buffers
   */
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

    @Nullable
    FeatureFlagEntry globalEntry = globalFlags == null || globalIndex < 0 ? null : globalFlags.get(globalIndex);
    @Nullable
    FeatureFlagEntry isolationEntry =
        isolationFlags == null || isolationIndex < 0 ? null : isolationFlags.get(isolationIndex);
    @Nullable
    FeatureFlagEntry currentEntry = currentFlags == null || currentIndex < 0 ? null : currentFlags.get(currentIndex);

    final @NotNull java.util.Map<String, FeatureFlagEntry> uniqueFlags =
        new java.util.LinkedHashMap<>(maxSize);

    // check if there is still room and remaining items to check
    while (uniqueFlags.size() < maxSize
        && (globalEntry != null || isolationEntry != null || currentEntry != null)) {

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
          currentEntry =
              currentFlags != null && currentIndex >= 0 ? currentFlags.get(currentIndex) : null;
        } else if (ScopeType.ISOLATION.equals(selectedBuffer)) {
          isolationIndex--;
          isolationEntry =
              isolationFlags != null && isolationIndex >= 0
                  ? isolationFlags.get(isolationIndex)
                  : null;
        } else if (ScopeType.GLOBAL.equals(selectedBuffer)) {
          globalIndex--;
          globalEntry =
              globalFlags != null && globalIndex >= 0 ? globalFlags.get(globalIndex) : null;
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
