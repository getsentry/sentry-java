package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.TestOnly;

/** The user affected by an event. */
public final class User implements Cloneable, IUnknownPropertiesConsumer {
  private String email;
  private String id;
  private String username;
  private String ipAddress;
  private Map<String, String> other;
  private Map<String, Object> unknown;

  /**
   * Gets the e-mail address of the user.
   *
   * @return the e-mail.
   */
  public String getEmail() {
    return email;
  }

  /**
   * Gets the e-mail address of the user.
   *
   * @param email the e-mail.
   */
  public void setEmail(String email) {
    this.email = email;
  }

  /**
   * Gets the id of the user.
   *
   * @return the id.
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the id of the user.
   *
   * @param id the user id.
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Gets the username of the user.
   *
   * @return the username.
   */
  public String getUsername() {
    return username;
  }

  /**
   * Sets the username of the user.
   *
   * @param username the username.
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * Gets the IP address of the user.
   *
   * @return the IP address of the user.
   */
  public String getIpAddress() {
    return ipAddress;
  }

  /**
   * Sets the IP address of the user.
   *
   * @param ipAddress the IP address of the user.
   */
  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  /**
   * Gets other user related data.
   *
   * @return the other user data.
   */
  public Map<String, String> getOthers() {
    return other;
  }

  /**
   * Sets other user related data.
   *
   * @param other the other user related data..
   */
  public void setOthers(Map<String, String> other) {
    this.other = other;
  }

  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }

  @TestOnly
  Map<String, Object> getUnknown() {
    return unknown;
  }

  @Override
  public User clone() throws CloneNotSupportedException {
    User clone = (User) super.clone();

    if (other != null) {
      Map<String, String> otherClone = new ConcurrentHashMap<>();

      for (Map.Entry<String, String> item : other.entrySet()) {
        if (item != null) {
          otherClone.put(item.getKey(), item.getValue());
        }
      }

      clone.other = otherClone;
    } else {
      clone.other = null;
    }

    if (unknown != null) {
      Map<String, Object> unknownClone = new HashMap<>();

      for (Map.Entry<String, Object> item : unknown.entrySet()) {
        if (item != null) {
          unknownClone.put(item.getKey(), item.getValue());
        }
      }

      clone.unknown = unknownClone;
    } else {
      clone.unknown = null;
    }

    return clone;
  }
}
