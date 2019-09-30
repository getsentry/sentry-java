package io.sentry;

import io.sentry.protocol.Request;
import io.sentry.protocol.SdkVersion;
import io.sentry.protocol.User;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class Scope {
  private SentryLevel level;
  private String transaction;
  private String environment;
  private User user;
  private Request request;
  private SdkVersion sdkVersion;
  private List<String> fingerprint = new ArrayList<>();
  private CopyOnWriteArrayList<Breadcrumb> breadcrumbs = new CopyOnWriteArrayList<>();
  private ConcurrentHashMap<String, String> tags = new ConcurrentHashMap<>();
  private ConcurrentHashMap<String, Object> extra = new ConcurrentHashMap<>();

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

  public String getEnvironment() {
    return environment;
  }

  public void setEnvironment(String environment) {
    this.environment = environment;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Request getRequest() {
    return request;
  }

  public void setRequest(Request request) {
    this.request = request;
  }

  public SdkVersion getSdkVersion() {
    return sdkVersion;
  }

  public void setSdkVersion(SdkVersion sdkVersion) {
    this.sdkVersion = sdkVersion;
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

  public ConcurrentHashMap<String, String> getTags() {
    return tags;
  }

  public void setTag(String key, String value) {
    this.tags.put(key, value);
  }

  public ConcurrentHashMap<String, Object> getExtra() {
    return extra;
  }

  public void setExtra(String key, String value) {
    this.extra.put(key, value);
  }
}
