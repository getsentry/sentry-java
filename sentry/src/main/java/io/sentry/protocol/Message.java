package io.sentry.protocol;

import io.sentry.IUnknownPropertiesConsumer;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.ApiStatus;

// https://docs.sentry.io/development/sdk-dev/event-payloads/message/

/**
 * A log entry message.
 *
 * <p>A log message is similar to the `message` attribute on the event itself but can additionally
 * hold optional parameters.
 *
 * <p>```json { "message": { "message": "My raw message with interpreted strings like %s", "params":
 * ["this"] } } ```
 *
 * <p>```json { "message": { "message": "My raw message with interpreted strings like {foo}",
 * "params": {"foo": "this"} } } ```
 */
public final class Message implements IUnknownPropertiesConsumer {
  /**
   * The formatted message. If `message` and `params` are given, Sentry will attempt to backfill
   * `formatted` if empty.
   *
   * <p>It must not exceed 8192 characters. Longer messages will be truncated.
   */
  private String formatted;
  /**
   * The log message with parameter placeholders.
   *
   * <p>This attribute is primarily used for grouping related events together into issues. Therefore
   * this really should just be a string template, i.e. `Sending %d requests` instead of `Sending
   * 9999 requests`. The latter is much better at home in `formatted`.
   *
   * <p>It must not exceed 8192 characters. Longer messages will be truncated.
   */
  private String message;
  /**
   * Parameters to be interpolated into the log message. This can be an array of positional
   * parameters as well as a mapping of named arguments to their values.
   */
  private List<String> params;

  @SuppressWarnings("unused")
  private Map<String, Object> unknown;

  public String getFormatted() {
    return formatted;
  }

  /**
   * Sets a formatted String
   *
   * @param formatted a formatted String
   */
  public void setFormatted(String formatted) {
    this.formatted = formatted;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public List<String> getParams() {
    return params;
  }

  public void setParams(List<String> params) {
    this.params = params;
  }

  @ApiStatus.Internal
  @Override
  public void acceptUnknownProperties(Map<String, Object> unknown) {
    this.unknown = unknown;
  }
}
