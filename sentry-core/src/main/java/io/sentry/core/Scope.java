package io.sentry.core;

import io.sentry.core.protocol.User;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Scope implements Cloneable {
  private @Nullable SentryLevel level;
  private @Nullable String transaction;
  private @Nullable User user;
  private @NotNull List<String> fingerprint = new ArrayList<>();
  private @NotNull Queue<Breadcrumb> breadcrumbs;
  private @NotNull Map<String, String> tags = new ConcurrentHashMap<>();
  private @NotNull Map<String, Object> extra = new ConcurrentHashMap<>();
  private @NotNull List<EventProcessor> eventProcessors = new ArrayList<>();
  private final @NotNull SentryOptions options;

  public Scope(final @NotNull SentryOptions options) {
    this.options = options;
    this.breadcrumbs = createBreadcrumbsList(options.getMaxBreadcrumbs());
  }

  public @Nullable SentryLevel getLevel() {
    return level;
  }

  public void setLevel(@Nullable SentryLevel level) {
    this.level = level;
  }

  public @Nullable String getTransaction() {
    return transaction;
  }

  public void setTransaction(@Nullable String transaction) {
    this.transaction = transaction;
  }

  public @Nullable User getUser() {
    return user;
  }

  public void setUser(@Nullable User user) {
    this.user = user;
  }

  @NotNull
  List<String> getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(@NotNull List<String> fingerprint) {
    this.fingerprint = fingerprint;
  }

  @NotNull
  Queue<Breadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  private @Nullable Breadcrumb executeBeforeBreadcrumb(
      final @NotNull SentryOptions.BeforeBreadcrumbCallback callback,
      @NotNull Breadcrumb breadcrumb,
      final @Nullable Object hint) {
    try {
      breadcrumb = callback.execute(breadcrumb, hint);
    } catch (Exception e) {
      options
          .getLogger()
          .log(
              SentryLevel.ERROR,
              "The BeforeBreadcrumbCallback callback threw an exception. It will be added as breadcrumb and continue.",
              e);

      Map<String, String> data = breadcrumb.getData();
      if (breadcrumb.getData() == null) {
        data = new HashMap<>();
      }
      data.put("sentry:message", e.getMessage());
      breadcrumb.setData(data);
    }
    return breadcrumb;
  }

  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb, final @Nullable Object hint) {
    if (breadcrumb == null) {
      return;
    }

    SentryOptions.BeforeBreadcrumbCallback callback = options.getBeforeBreadcrumb();
    if (callback != null) {
      breadcrumb = executeBeforeBreadcrumb(callback, breadcrumb, hint);
    }
    if (breadcrumb != null) {
      this.breadcrumbs.add(breadcrumb);
    } else {
      options.getLogger().log(SentryLevel.INFO, "Breadcrumb was dropped by beforeBreadcrumb");
    }
  }

  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {
    addBreadcrumb(breadcrumb, null);
  }

  public void clearBreadcrumbs() {
    breadcrumbs.clear();
  }

  public void clear() {
    level = null;
    transaction = null;
    user = null;
    fingerprint.clear();
    breadcrumbs.clear();
    tags.clear();
    extra.clear();
    eventProcessors.clear();
  }

  @NotNull
  Map<String, String> getTags() {
    return tags;
  }

  public void setTag(@NotNull String key, @NotNull String value) {
    this.tags.put(key, value);
  }

  public void removeTag(@NotNull String key) {
    this.tags.remove(key);
  }

  @NotNull
  Map<String, Object> getExtras() {
    return extra;
  }

  public void setExtra(@NotNull String key, @NotNull String value) {
    this.extra.put(key, value);
  }

  public void removeExtra(@NotNull String key) {
    this.extra.remove(key);
  }

  private @NotNull Queue<Breadcrumb> createBreadcrumbsList(final int maxBreadcrumb) {
    return SynchronizedQueue.synchronizedQueue(new CircularFifoQueue<>(maxBreadcrumb));
  }

  @Override
  public Scope clone() throws CloneNotSupportedException {
    Scope clone = (Scope) super.clone();
    clone.level = level != null ? SentryLevel.valueOf(level.name().toUpperCase(Locale.ROOT)) : null;
    clone.user = user != null ? user.clone() : null;
    clone.fingerprint = fingerprint != null ? new ArrayList<>(fingerprint) : null;
    clone.eventProcessors = eventProcessors != null ? new ArrayList<>(eventProcessors) : null;

    if (breadcrumbs != null) {
      Queue<Breadcrumb> breadcrumbsClone = createBreadcrumbsList(options.getMaxBreadcrumbs());

      for (Breadcrumb item : breadcrumbs) {
        Breadcrumb breadcrumbClone = item.clone();
        breadcrumbsClone.add(breadcrumbClone);
      }
      clone.breadcrumbs = breadcrumbsClone;
    } else {
      clone.breadcrumbs = null;
    }

    if (tags != null) {
      Map<String, String> tagsClone = new ConcurrentHashMap<>();

      for (Map.Entry<String, String> item : tags.entrySet()) {
        if (item != null) {
          tagsClone.put(item.getKey(), item.getValue());
        }
      }

      clone.tags = tagsClone;
    } else {
      clone.tags = null;
    }

    if (extra != null) {
      Map<String, Object> extraClone = new HashMap<>();

      for (Map.Entry<String, Object> item : extra.entrySet()) {
        if (item != null) {
          extraClone.put(item.getKey(), item.getValue());
        }
      }

      clone.extra = extraClone;
    } else {
      clone.extra = null;
    }

    return clone;
  }

  @NotNull
  List<EventProcessor> getEventProcessors() {
    return eventProcessors;
  }

  public void addEventProcessor(@NotNull EventProcessor eventProcessor) {
    eventProcessors.add(eventProcessor);
  }
}
