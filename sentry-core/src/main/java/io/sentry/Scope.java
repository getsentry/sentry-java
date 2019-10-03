package io.sentry;

import io.sentry.protocol.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Scope {
  private SentryLevel level;
  private String transaction;
  private User user;
  private List<String> fingerprint = new ArrayList<>();
  private List<Breadcrumb> breadcrumbs = new CopyOnWriteArrayList<>();
  private Map<String, String> tags = new ConcurrentHashMap<>();
  private Map<String, Object> extra = new ConcurrentHashMap<>();

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

  public List<Breadcrumb> getBreadcrumbs() {
    return breadcrumbs;
  }

  public void addBreadcrumb(Breadcrumb breadcrumb) {
    if (breadcrumb == null) {
      return;
    }
    this.breadcrumbs.add(breadcrumb);
  }

  public Map<String, String> getTags() {
    return tags;
  }

  public void setTag(String key, String value) {
    this.tags.put(key, value);
  }

  public Map<String, Object> getExtra() {
    return extra;
  }

  public void setExtra(String key, String value) {
    this.extra.put(key, value);
  }
}
