package io.sentry.protocol;

/*
/ Describes a thread in the Sentry protocol.
*/
public class SentryThread {
  private Integer id;
  private String name;
  private Boolean crashed;
  private Boolean current;
  private SentryStackTrace stacktrace;

  /*
  / Gets the Id of the thread
  */
  public Integer getId() {
    return id;
  }

  /*
  / Sets the Id of the thread
  */
  public void setId(Integer id) {
    this.id = id;
  }

  /*
  / Gets the name of the thread
  */
  public String getName() {
    return name;
  }

  /*
  / Sets the name of the thread
  */
  public void setName(String name) {
    this.name = name;
  }

  /*
  / Gets whether the crash happened on this thread.
  */
  public Boolean getCrashed() {
    return crashed;
  }

  /*
  / Sets whether the crash happened on this thread.
  */
  public void setCrashed(Boolean crashed) {
    this.crashed = crashed;
  }

  /*
  / Get an optional flag to indicate that the thread was in the foreground.
  */
  public Boolean getCurrent() {
    return current;
  }

  /*
  / Sets an optional flag to indicate that the thread was in the foreground.
  */
  public void setCurrent(Boolean current) {
    this.current = current;
  }

  /*
  / Gets the stack trace of the thread.
  */
  public SentryStackTrace getStacktrace() {
    return stacktrace;
  }

  /*
  / Sets the stack trace of the thread.
  */
  public void setStacktrace(SentryStackTrace stacktrace) {
    this.stacktrace = stacktrace;
  }
}
