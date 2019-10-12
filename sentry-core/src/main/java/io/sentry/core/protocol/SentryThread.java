package io.sentry.core.protocol;

/** Describes a thread in the Sentry protocol. */
public class SentryThread {
  private Integer id;
  private String name;
  private Boolean crashed;
  private Boolean current;
  private SentryStackTrace stacktrace;

  /**
   * Gets the Id of the thread.
   *
   * @return the thread id.
   */
  public Integer getId() {
    return id;
  }

  /**
   * Sets the Id of the thread.
   *
   * @param id the thread id.
   */
  public void setId(Integer id) {
    this.id = id;
  }

  /**
   * Gets the name of the thread.
   *
   * @return the name of the thread.
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name of the thread.
   *
   * @param name the name of the thread.
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets whether the crash happened on this thread.
   *
   * @return whether it was the crashed thread.
   */
  public Boolean getCrashed() {
    return crashed;
  }

  /**
   * Sets whether the crash happened on this thread.
   *
   * @param crashed whether it was the crashed thread.
   */
  public void setCrashed(Boolean crashed) {
    this.crashed = crashed;
  }

  /**
   * Get an optional flag to indicate that the thread was in the foreground.
   *
   * @return whether the thread was in the foreground.
   */
  public Boolean getCurrent() {
    return current;
  }

  /**
   * Sets an optional flag to indicate that the thread was in the foreground.
   *
   * @param current whether the thread was in the foreground.
   */
  public void setCurrent(Boolean current) {
    this.current = current;
  }

  /**
   * Gets the stacktrace of the thread.
   *
   * @return the thread stacktrace.
   */
  public SentryStackTrace getStacktrace() {
    return stacktrace;
  }

  /**
   * Sets the stacktrace of the thread.
   *
   * @param stacktrace the thread stacktrace.
   */
  public void setStacktrace(SentryStackTrace stacktrace) {
    this.stacktrace = stacktrace;
  }
}
