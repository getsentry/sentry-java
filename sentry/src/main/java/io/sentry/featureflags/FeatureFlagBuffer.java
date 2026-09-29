package io.sentry.featureflags;

import io.sentry.ISentryLifecycleToken;
import io.sentry.ScopeType;
import io.sentry.SentryOptions;
import io.sentry.protocol.FeatureFlag;
import io.sentry.protocol.FeatureFlags;
import io.sentry.util.AutoClosableReentrantLock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Feature flag buffer implementation optimized for usage in scopes.
 *
 * <ul>
 *   <li>When full, the oldest entry is evicted
 *   <li>Updates to existing entries refresh the entry, meaning it'll be dropped last
 *   <li>Performance of scope cloning is optimized here
 *   <li>Supports merging across scope types (GLOBAL, ISOLATION, CURRENT)
 * </ul>
 *
 * <p>{@link #flags} always holds an immutable list. Writers hold {@link #lock} and swap in a new
 * list; readers take a single volatile read and are then free to iterate or index into a list that
 * can no longer change. This is what makes {@link #clone()} free: the copy shares the list rather
 * than duplicating it.
 */
@ApiStatus.Internal
public final class FeatureFlagBuffer implements IFeatureFlagBuffer {

  private volatile @NotNull List<FeatureFlagEntry> flags;
  private final @NotNull AutoClosableReentrantLock lock = new AutoClosableReentrantLock();
  private final int maxSize;

  private FeatureFlagBuffer(final int maxSize) {
    this(maxSize, Collections.<FeatureFlagEntry>emptyList());
  }

  private FeatureFlagBuffer(final int maxSize, final @NotNull List<FeatureFlagEntry> flags) {
    this.maxSize = maxSize;
    this.flags = flags;
  }

  @Override
  public void add(final @Nullable String flag, final @Nullable Boolean result) {
    if (flag == null || result == null) {
      return;
    }
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      final @NotNull List<FeatureFlagEntry> current = flags;
      final @NotNull List<FeatureFlagEntry> updated = new ArrayList<>(current.size() + 1);
      for (final @NotNull FeatureFlagEntry entry : current) {
        if (!entry.flag.equals(flag)) {
          updated.add(entry);
        }
      }
      updated.add(new FeatureFlagEntry(flag, result, System.nanoTime()));

      if (updated.size() > maxSize) {
        updated.remove(0);
      }

      flags = Collections.unmodifiableList(updated);
    }
  }

  @Override
  public void clear() {
    try (final @NotNull ISentryLifecycleToken ignored = lock.acquire()) {
      flags = Collections.emptyList();
    }
  }

  @Override
  public @Nullable FeatureFlags getFeatureFlags() {
    final @NotNull List<FeatureFlagEntry> snapshot = flags;
    final @NotNull List<FeatureFlag> featureFlags = new ArrayList<>(snapshot.size());
    for (final @NotNull FeatureFlagEntry entry : snapshot) {
      featureFlags.add(entry.toFeatureFlag());
    }
    return new FeatureFlags(featureFlags);
  }

  @Override
  public @NotNull IFeatureFlagBuffer clone() {
    return new FeatureFlagBuffer(maxSize, flags);
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
   * <p>Entries carrying the same timestamp are resolved in favour of the most specific scope, so
   * CURRENT wins over ISOLATION, which in turn wins over GLOBAL.
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

    // One volatile read each pins an immutable list, so concurrent writers cannot shift these
    // out from under the index arithmetic below
    final @Nullable List<FeatureFlagEntry> globalFlags =
        globalBuffer == null ? null : globalBuffer.flags;
    final @Nullable List<FeatureFlagEntry> isolationFlags =
        isolationBuffer == null ? null : isolationBuffer.flags;
    final @Nullable List<FeatureFlagEntry> currentFlags =
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
    FeatureFlagEntry globalEntry =
        globalFlags == null || globalIndex < 0 ? null : globalFlags.get(globalIndex);
    @Nullable
    FeatureFlagEntry isolationEntry =
        isolationFlags == null || isolationIndex < 0 ? null : isolationFlags.get(isolationIndex);
    @Nullable
    FeatureFlagEntry currentEntry =
        currentFlags == null || currentIndex < 0 ? null : currentFlags.get(currentIndex);

    final @NotNull Map<String, FeatureFlagEntry> uniqueFlags = new LinkedHashMap<>(maxSize);

    // check if there is still room and remaining items to check
    while (uniqueFlags.size() < maxSize
        && (globalEntry != null || isolationEntry != null || currentEntry != null)) {

      @Nullable FeatureFlagEntry entryToAdd = null;
      @Nullable ScopeType selectedBuffer = null;

      // choose newest entry across all buffers
      if (globalEntry != null) {
        entryToAdd = globalEntry;
        selectedBuffer = ScopeType.GLOBAL;
      }
      if (isolationEntry != null
          && (entryToAdd == null || isolationEntry.nanos >= entryToAdd.nanos)) {
        entryToAdd = isolationEntry;
        selectedBuffer = ScopeType.ISOLATION;
      }
      if (currentEntry != null && (entryToAdd == null || currentEntry.nanos >= entryToAdd.nanos)) {
        entryToAdd = currentEntry;
        selectedBuffer = ScopeType.CURRENT;
      }

      if (entryToAdd == null) {
        break;
      }

      // no need to update existing entries since we already have the latest
      if (!uniqueFlags.containsKey(entryToAdd.flag)) {
        uniqueFlags.put(entryToAdd.flag, entryToAdd);
      }

      // decrement only index of buffer that was selected
      if (selectedBuffer == ScopeType.CURRENT) {
        currentIndex--;
        currentEntry =
            currentFlags != null && currentIndex >= 0 ? currentFlags.get(currentIndex) : null;
      } else if (selectedBuffer == ScopeType.ISOLATION) {
        isolationIndex--;
        isolationEntry =
            isolationFlags != null && isolationIndex >= 0
                ? isolationFlags.get(isolationIndex)
                : null;
      } else if (selectedBuffer == ScopeType.GLOBAL) {
        globalIndex--;
        globalEntry = globalFlags != null && globalIndex >= 0 ? globalFlags.get(globalIndex) : null;
      }
    }

    // Convert to list in reverse order (oldest first, newest last)
    final @NotNull List<FeatureFlagEntry> resultList = new ArrayList<>(uniqueFlags.values());
    Collections.reverse(resultList);
    return new FeatureFlagBuffer(maxSize, Collections.unmodifiableList(resultList));
  }

  private static class FeatureFlagEntry {

    private final @NotNull String flag;
    private final boolean result;
    private final long nanos;

    public FeatureFlagEntry(final @NotNull String flag, final boolean result, final long nanos) {
      this.flag = flag;
      this.result = result;
      this.nanos = nanos;
    }

    public @NotNull FeatureFlag toFeatureFlag() {
      return new FeatureFlag(flag, result);
    }
  }
}
