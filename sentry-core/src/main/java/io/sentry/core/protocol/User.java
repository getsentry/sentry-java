package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import io.sentry.core.util.CollectionUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/** The user affected by an event. */
public final class User implements Cloneable, IUnknownPropertiesConsumer {

  /** User's email */
  private @Nullable String email;

  /** User's id */
  private @Nullable String id;

  /** User's username */
  private @Nullable String username;

  /** User's ipAddress */
  private @Nullable String ipAddress;

  /** User's others map */
  private @Nullable Map<String, String> other;

  /** unknown fields, only internal usage. */
  private @Nullable Map<String, Object> unknown;

  /**
   * Gets the e-mail address of the user.
   *
   * @return the e-mail.
   */
  public @Nullable String getEmail() {
    return email;
  }

  /**
   * Gets the e-mail address of the user.
   *
   * @param email the e-mail.
   */
  public void setEmail(@Nullable String email) {
    this.email = email;
  }

  /**
   * Gets the id of the user.
   *
   * @return the id.
   */
  public @Nullable String getId() {
    return id;
  }

  /**
   * Sets the id of the user.
   *
   * @param id the user id.
   */
  public void setId(@Nullable String id) {
    this.id = id;
  }

  /**
   * Gets the username of the user.
   *
   * @return the username.
   */
  public @Nullable String getUsername() {
    return username;
  }

  /**
   * Sets the username of the user.
   *
   * @param username the username.
   */
  public void setUsername(@Nullable String username) {
    this.username = username;
  }

  /**
   * Gets the IP address of the user.
   *
   * @return the IP address of the user.
   */
  public @Nullable String getIpAddress() {
    return ipAddress;
  }

  /**
   * Sets the IP address of the user.
   *
   * @param ipAddress the IP address of the user.
   */
  public void setIpAddress(@Nullable String ipAddress) {
    this.ipAddress = ipAddress;
  }

  /**
   * Gets other user related data.
   *
   * @return the other user data.
   */
  public @Nullable Map<String, String> getOthers() {
    return other;
  }

  /**
   * Sets other user related data.
   *
   * @param other the other user related data..
   */
  public void setOthers(@Nullable Map<String, String> other) {
    this.other = new ConcurrentHashMap<>(other);
  }

  /**
   * User's unknown fields, only internal usage
   *
   * @param unknown the unknown fields
   */
  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = new ConcurrentHashMap<>(unknown);
  }

  /**
   * the User's unknown fields
   *
   * @return the unknown map
   */
  @TestOnly
  Map<String, Object> getUnknown() {
    return unknown;
  }

  /**
   * Clones an User aka deep copy
   *
   * @return the cloned User
   * @throws CloneNotSupportedException if the User is not cloneable
   */
  @Override
  public @NotNull User clone() throws CloneNotSupportedException {
    final User clone = (User) super.clone();

    clone.other = CollectionUtils.shallowCopy(other);
    clone.unknown = CollectionUtils.shallowCopy(unknown);

    return clone;
  }
}
