package io.sentry;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApiStatus.Internal
public final class HubStore {
  private static class SingletonHolder {

    public static final HubStore instance = new HubStore();

  }
  public static HubStore getInstance() {
    return SingletonHolder.instance;
  }

  private static final @NotNull ThreadLocal<HubContainer> currentThreadHub = new ThreadLocal<>();

  private final @NotNull ConcurrentHashMap<String, HubStorageEntry> hubs = new ConcurrentHashMap<>();
  // this only serves to keep a strong ref on the IHub until it is removed explicitly

  public @Nullable IHub get() {
    return get(getDefaultHubId());
  }

  public @Nullable IHub get(@NotNull final String hubId) {
    @Nullable HubStorageEntry entry = getEntry(hubId);
    if (entry != null) {
      return entry.hub.get();
    }
    return null;
  }

  private @Nullable HubStorageEntry getEntry(@NotNull final String hubId) {
    return hubs.get(hubId);
  }

  public void set(@NotNull final IHub hub) {
    String threadId = getDefaultHubId();
    set(threadId, HubIdType.THREAD_ID, hub);
  }

  public void remove() {
    remove(getDefaultHubId());
  }

  public void remove(@NotNull final String hubId) {
    @Nullable HubStorageEntry entry = getEntry(hubId);
    if (entry != null) {
      entry.cleanup();
    }
    hubs.remove(hubId);
  }

  // TODO find places to call cleanup
  public void cleanup() {
    @NotNull final Set<Map.Entry<String, HubStorageEntry>> entries = ((Map<String, HubStorageEntry>) hubs).entrySet();
    @NotNull final Set<String> toBeRemoved = new HashSet<>();
    @NotNull final Set<Thread> threads = Thread.getAllStackTraces().keySet();
    @NotNull final Set<String> threadIds = new HashSet<>();

    for (final Thread t: threads) {
      threadIds.add(String.valueOf(t.getId()));
    }

    for (final Map.Entry<String, HubStorageEntry> entry : entries) {
      @NotNull final String hubId = entry.getKey();
      @NotNull final HubStorageEntry value = entry.getValue();
      if (value.hub.get() == null) {
        toBeRemoved.add(hubId);
      }
      if (HubIdType.THREAD_ID.equals(value.hubIdType)) {
        if (!threadIds.contains(hubId)) {
          toBeRemoved.add(hubId);
        }
      }
    }

    for (String hubId: toBeRemoved) {
      remove(hubId);
    }
  }

  private String getDefaultHubId() {
    // TODO is it a good idea to use thread id? how fast will it be reused?
    // thread name could be changed and thus be even worse as key
    return String.valueOf(Thread.currentThread().getId());
  }

  public void set(@NotNull final String hubId, @NotNull HubIdType hubIdType, @NotNull final IHub hub) {
    HubContainer hubContainer = new HubContainer(hub);
    if (HubIdType.THREAD_ID.equals(hubIdType)) {
      currentThreadHub.set(hubContainer);
    }
    hubs.put(hubId, new HubStorageEntry(hubIdType, hub, hubContainer));
  }

  public enum HubIdType {
    THREAD_ID,
    WEBFLUX_CONTEXT_ID,
    UNKNOWN
  }

  private static final class HubStorageEntry {
    private @NotNull final HubIdType hubIdType;
    private @NotNull final WeakReference<IHub> hub;
    private @NotNull final WeakReference<HubContainer> hubContainer;

    private HubStorageEntry(@NotNull HubIdType hubIdType, @NotNull IHub hub, @NotNull HubContainer hubContainer) {
      this.hubIdType = hubIdType;
      this.hub = new WeakReference<>(hub);
      this.hubContainer = new WeakReference<>(hubContainer);
    }

    private void cleanup() {
      @Nullable HubContainer hubContainer = this.hubContainer.get();
      if (hubContainer != null) {
        hubContainer.unset();
      }
    }
  }
}
