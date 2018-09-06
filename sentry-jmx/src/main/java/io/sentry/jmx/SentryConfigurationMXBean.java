package io.sentry.jmx;

public interface SentryConfigurationMXBean {
  void reinitialize(String dsn);
  String getRelease();
  void setRelease(String release);
  String getDist();
  void setDist(String dist);
  String getServerName();
  void setServerName(String serverName);
}
