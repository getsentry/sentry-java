package io.sentry.core.protocol;

import io.sentry.core.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/** The Sentry Exception interface. */
public final class SentryException implements IUnknownPropertiesConsumer {
  private String type;
  private String value;
  private String module;
  private Long threadId;
  private SentryStackTrace stacktrace;
  private Mechanism mechanism;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  /**
   * The Exception Type.
   *
   * @return the type of the exception.
   */
  public String getType() {
    return type;
  }

  /**
   * The Exception Type.
   *
   * @param type type of the exception.
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * The exception value.
   *
   * @return the value.
   */
  public String getValue() {
    return value;
  }

  /** The exception value. */
  public void setValue(String value) {
    this.value = value;
  }

  /**
   * Gets the optional module, or package which the exception type lives in.
   *
   * @return the module.
   */
  public String getModule() {
    return module;
  }

  /**
   * Sets the optional module, or package which the exception type lives in.
   *
   * @param module the module.
   */
  public void setModule(String module) {
    this.module = module;
  }

  /**
   * Gets an optional value which refers to a thread in the threads interface.
   *
   * @return the thread id.
   */
  public Long getThreadId() {
    return threadId;
  }

  /**
   * Sets an optional value which refers to a thread in the threads interface.
   *
   * @param threadId the thread id.
   */
  public void setThreadId(Long threadId) {
    this.threadId = threadId;
  }

  /**
   * Gets the stack trace.
   *
   * @return the stacktrace.
   */
  public SentryStackTrace getStacktrace() {
    return stacktrace;
  }

  /**
   * Sets the stack trace.
   *
   * @param stacktrace the stacktrace of the exception.
   */
  public void setStacktrace(SentryStackTrace stacktrace) {
    this.stacktrace = stacktrace;
  }

  /**
   * Gets an optional mechanism that created this exception.
   *
   * @return the mechanism.
   */
  public Mechanism getMechanism() {
    return mechanism;
  }

  /**
   * Sets an optional mechanism that created this exception.
   *
   * @param mechanism the mechanism.
   */
  public void setMechanism(Mechanism mechanism) {
    this.mechanism = mechanism;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
