package io.sentry;

/** A direction of automatically collected HTTP body content. */
public enum HttpBodyType {
  /** A request received by a server integration. */
  INCOMING_REQUEST,
  /** A request sent by a client integration. */
  OUTGOING_REQUEST,
  /** A response received by a client integration. */
  INCOMING_RESPONSE,
  /** A response sent by a server integration. */
  OUTGOING_RESPONSE
}
