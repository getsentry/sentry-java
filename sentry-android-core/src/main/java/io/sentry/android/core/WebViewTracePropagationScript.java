package io.sentry.android.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Builds the script that writes the native trace into a WebView document as {@code sentry-trace}
 * and {@code baggage} meta tags, plus the origin rules that gate where it runs. Has no androidx
 * dependency so it can be used before the androidx.webkit availability check.
 */
final class WebViewTracePropagationScript {

  /**
   * {@code baggage} key carrying the root span's name. It is free-form text and can contain user
   * data (for example a parameterized URL), so it is removed before it reaches the WebView page.
   */
  private static final @NotNull String SENTRY_TRANSACTION_BAGGAGE_KEY = "sentry-transaction";

  private static final @NotNull Pattern SCHEME =
      Pattern.compile("^[a-z][a-z0-9+.-]*://", Pattern.CASE_INSENSITIVE);
  private static final @NotNull Pattern PORT = Pattern.compile(":\\d+$");
  private static final @NotNull Pattern IPV4 = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

  private WebViewTracePropagationScript() {}

  /**
   * Normalizes each entry with {@link #normalizeHost(String)}, dropping blank entries and
   * duplicates.
   */
  static @NotNull List<String> normalizeHosts(final @NotNull Collection<String> allowedHosts) {
    final @NotNull Set<String> normalized = new LinkedHashSet<>();
    for (final @Nullable String host : allowedHosts) {
      if (host == null) {
        continue;
      }
      final @NotNull String normalizedHost = normalizeHost(host);
      if (!normalizedHost.isEmpty()) {
        normalized.add(normalizedHost);
      }
    }
    return new ArrayList<>(normalized);
  }

  /**
   * Normalizes an {@code allowedHosts} entry to a bare lowercase hostname so it can be compared
   * against the page's {@code location.hostname}. Tolerates surrounding whitespace, a scheme, a
   * path, a port, a trailing dot, and a leading subdomain wildcard (e.g. {@code "
   * https://*.App.Example.com:8080/x "} becomes {@code "app.example.com"}). IPv6 literals are
   * bracketed to match {@code location.hostname} ({@code "::1"} becomes {@code "[::1]"}).
   */
  static @NotNull String normalizeHost(final @NotNull String host) {
    String hostAndPort = SCHEME.matcher(host.trim()).replaceFirst("");

    final int pathStart = indexOfAny(hostAndPort, "/?#");
    if (pathStart != -1) {
      hostAndPort = hostAndPort.substring(0, pathStart);
    }

    // Strip the port without mistaking an IPv6 hextet for one.
    final int closingBracket = hostAndPort.indexOf(']');
    if (closingBracket != -1) {
      hostAndPort = hostAndPort.substring(0, closingBracket + 1);
    } else if (hostAndPort.indexOf(':') != hostAndPort.lastIndexOf(':')) {
      hostAndPort = "[" + hostAndPort + "]";
    } else {
      hostAndPort = PORT.matcher(hostAndPort).replaceFirst("");
    }

    if (hostAndPort.endsWith(".")) {
      hostAndPort = hostAndPort.substring(0, hostAndPort.length() - 1);
    }
    // Subdomains always match, so a subdomain wildcard is redundant. Kept, it would never match
    // the page's hostname and would make the origin rules invalid.
    while (hostAndPort.startsWith("*.")) {
      hostAndPort = hostAndPort.substring(2);
    }
    return hostAndPort.toLowerCase(Locale.ROOT);
  }

  /**
   * Origin rules for {@code WebViewCompat.addDocumentStartJavaScript}: each host and its subdomains
   * over https. IP literals cannot take a subdomain wildcard.
   */
  static @NotNull Set<String> originRules(final @NotNull List<String> normalizedHosts) {
    final @NotNull Set<String> rules = new LinkedHashSet<>();
    for (final @NotNull String host : normalizedHosts) {
      rules.add("https://" + host);
      if (!isIpLiteral(host)) {
        rules.add("https://*." + host);
      }
    }
    return rules;
  }

  /**
   * Removes the {@code sentry-transaction} entry from a {@code baggage} header, keeping every other
   * entry. Returns null when nothing is left to propagate.
   */
  static @Nullable String stripTransactionFromBaggage(final @Nullable String baggage) {
    if (baggage == null) {
      return null;
    }
    final @NotNull StringBuilder result = new StringBuilder();
    for (final @NotNull String entry : baggage.split(",", -1)) {
      final @NotNull String trimmed = entry.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      final int separator = trimmed.indexOf('=');
      final @NotNull String key = separator == -1 ? trimmed : trimmed.substring(0, separator);
      if (SENTRY_TRANSACTION_BAGGAGE_KEY.equals(key.trim())) {
        continue;
      }
      if (result.length() > 0) {
        result.append(',');
      }
      result.append(trimmed);
    }
    return result.length() == 0 ? null : result.toString();
  }

  /**
   * Builds the script run at document start. Values are embedded as escaped string literals so any
   * character in them stays inert. The script only writes the tags in the main frame of a page
   * whose hostname is (a subdomain of) an allowed host, and does nothing if a {@code sentry-trace}
   * meta tag already exists.
   */
  static @NotNull String build(
      final @NotNull List<String> normalizedHosts,
      final @NotNull String sentryTrace,
      final @Nullable String baggage) {
    final @NotNull StringBuilder allowed = new StringBuilder("[");
    for (int i = 0; i < normalizedHosts.size(); i++) {
      if (i > 0) {
        allowed.append(',');
      }
      allowed.append(toJsStringLiteral(normalizedHosts.get(i)));
    }
    allowed.append(']');

    return "(function(){try{\n"
        + "if(window.top!==window)return;\n"
        + "var allowed="
        + allowed
        + ";\n"
        + "var host=(window.location&&window.location.hostname||'').toLowerCase();\n"
        + "if(!host)return;\n"
        + "if(!allowed.some(function(h){return host===h||host.slice(-(h.length+1))==='.'+h;}))return;\n"
        + "if(document.querySelector('meta[name=\"sentry-trace\"]'))return;\n"
        + "var head=document.head||document.documentElement;\n"
        + "if(!head)return;\n"
        + "var set=function(n,c){if(!c)return;var m=document.createElement('meta');"
        + "m.setAttribute('name',n);m.setAttribute('content',c);head.appendChild(m);};\n"
        + "set('sentry-trace',"
        + toJsStringLiteral(sentryTrace)
        + ");\n"
        + "set('baggage',"
        + (baggage == null ? "null" : toJsStringLiteral(baggage))
        + ");\n"
        + "}catch(e){}})();";
  }

  /**
   * Escapes a value as a double-quoted JavaScript string literal, like {@code JSON.stringify}.
   * Additionally escapes U+2028/U+2029 (line terminators in pre-ES2019 engines) and {@code <} so
   * the literal stays inert in any embedding context.
   */
  static @NotNull String toJsStringLiteral(final @NotNull String value) {
    final @NotNull StringBuilder sb = new StringBuilder(value.length() + 2);
    sb.append('"');
    for (int i = 0; i < value.length(); i++) {
      final char c = value.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        case '<':
        case ' ':
        case ' ':
          appendUnicodeEscape(sb, c);
          break;
        default:
          if (c < 0x20) {
            appendUnicodeEscape(sb, c);
          } else {
            sb.append(c);
          }
      }
    }
    sb.append('"');
    return sb.toString();
  }

  private static void appendUnicodeEscape(final @NotNull StringBuilder sb, final char c) {
    sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
  }

  private static boolean isIpLiteral(final @NotNull String host) {
    return host.startsWith("[") || IPV4.matcher(host).matches();
  }

  private static int indexOfAny(final @NotNull String value, final @NotNull String chars) {
    for (int i = 0; i < value.length(); i++) {
      if (chars.indexOf(value.charAt(i)) != -1) {
        return i;
      }
    }
    return -1;
  }
}
