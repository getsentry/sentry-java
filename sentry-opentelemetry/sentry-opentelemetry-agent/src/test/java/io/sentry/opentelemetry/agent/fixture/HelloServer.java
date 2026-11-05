package io.sentry.opentelemetry.agent.fixture;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * An app for the agent to instrument. It only uses the JDK, so a test can run it with nothing but
 * this class on the classpath, and it writes its port to the file named by {@code PORTFILE}.
 */
public final class HelloServer {
  public static void main(final String[] args) throws IOException {
    final HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
    server.createContext(
        "/hello",
        exchange -> {
          final byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
    Files.write(
        Paths.get(System.getenv("PORTFILE")),
        String.valueOf(server.getAddress().getPort()).getBytes(StandardCharsets.UTF_8));
  }
}
