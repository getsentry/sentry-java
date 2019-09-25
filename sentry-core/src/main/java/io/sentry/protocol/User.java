package io.sentry.protocol;

import java.util.Map;

public class User {
  private String email;
  private String id;
  private String username;
  private String ipAddress;
  private Map<String, String> other;

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public Map<String, String> getOther() {
    return other;
  }

  public void setOther(Map<String, String> other) {
    this.other = other;
  }
}
