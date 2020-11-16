package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

/**
 * A single exception.
 *
 * <p>Multiple values inside of an [event](#typedef-Event) represent chained exceptions and should
 * be sorted oldest to newest. For example, consider this Python code snippet:
 *
 * <p>```python try: raise Exception("random boring invariant was not met!") except Exception as e:
 * raise ValueError("something went wrong, help!") from e ```
 *
 * <p>`Exception` would be described first in the values list, followed by a description of
 * `ValueError`:
 *
 * <p>```json { "exception": { "values": [ {"type": "Exception": "value": "random boring invariant
 * was not met!"}, {"type": "ValueError", "value": "something went wrong, help!"}, ] } } ```
 */
public final class SentryException implements IUnknownPropertiesConsumer {
  /**
   * Exception type, e.g. `ValueError`.
   *
   * <p>At least one of `type` or `value` is required, otherwise the exception is discarded.
   */
  private String type;
  /**
   * Human readable display value.
   *
   * <p>At least one of `type` or `value` is required, otherwise the exception is discarded.
   */
  private String value;
  /** The optional module, or package which the exception type lives in. */
  private String module;
  /** An optional value that refers to a [thread](#typedef-Thread). */
  private Long threadId;
  /** Stack trace containing frames of this exception. */
  private SentryStackTrace stacktrace;
  /** Mechanism by which this exception was generated and handled. */
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

  /**
   * The exception value
   *
   * @param value The exception message
   */
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
