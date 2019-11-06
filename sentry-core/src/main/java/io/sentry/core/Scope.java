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

public final class Scope implements Cloneable {
  private SentryLevel level;
  private String transaction;
  private User user;
  private List<String> fingerprint = new ArrayList<>();
  private Queue<Breadcrumb> breadcrumbs;
  private Map<String, String> tags = new ConcurrentHashMap<>();
  private Map<String, Object> extra = new ConcurrentHashMap<>();
  private int maxBreadcrumb;

  public Scope(int maxBreadcrumb) {
    this.maxBreadcrumb = maxBreadcrumb;
    this.breadcrumbs = createBreadcrumbsList(this.maxBreadcrumb);
  }

  public SentryLevel getLevel() {
    return level;
  }

  public void setLevel(SentryLevel level) {
    this.level = level;
  }

  public String getTransaction() {
    return transaction;
  }

  public void setTransaction(String transaction) {
    this.transaction = transaction;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public List<String> getFingerprint() {
    return fingerprint;
  }

  public void setFingerprint(List<String> fingerprint) {
    this.fingerprint = fingerprint;
  }

  Queue<Breadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  public void addBreadcrumb(@NotNull Breadcrumb breadcrumb) {
    this.breadcrumbs.add(breadcrumb);
  }

  public void clearBreadcrumbs() {
    breadcrumbs.clear();
  }

  public Map<String, String> getTags() {
    return tags;
  }

  public void setTag(String key, String value) {
    this.tags.put(key, value);
  }

  public Map<String, Object> getExtras() {
    return extra;
  }

  public void setExtra(String key, String value) {
    this.extra.put(key, value);
  }

  private Queue<Breadcrumb> createBreadcrumbsList(final int maxBreadcrumb) {
    return SynchronizedQueue.synchronizedQueue(new CircularFifoQueue<>(maxBreadcrumb));
  }

  @Override
  public Scope clone() throws CloneNotSupportedException {
    Scope clone = (Scope) super.clone();
    clone.level = level != null ? SentryLevel.valueOf(level.name().toUpperCase(Locale.ROOT)) : null;
    clone.user = user != null ? user.clone() : null;
    clone.fingerprint = fingerprint != null ? new ArrayList<>(fingerprint) : null;

    if (breadcrumbs != null) {
      Queue<Breadcrumb> breadcrumbsClone = createBreadcrumbsList(maxBreadcrumb);

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
}
