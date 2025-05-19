package io.sentry;

public interface ISocketTagger {
  /** Tags the sockets traffic originating from the Sentry HttpConnection thread. */
  void tagSockets();

  /** Untags the sockets traffic originating from the Sentry HttpConnection thread. */
  void untagSockets();
}
