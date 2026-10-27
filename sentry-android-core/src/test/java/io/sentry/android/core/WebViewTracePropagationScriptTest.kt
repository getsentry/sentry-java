package io.sentry.android.core

import com.google.common.truth.Truth.assertThat
import kotlin.test.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.ScriptableObject

class WebViewTracePropagationScriptTest {

  private data class Meta(val name: String, val content: String)

  private data class Result(val metas: List<Meta>, val pwned: Boolean)

  /**
   * Runs the injection script against a minimal fake DOM, like the React Native SDK tests do, and
   * returns the meta tags present afterwards. A breakout payload would set `window.__pwned`.
   */
  private fun runInjection(
    script: String,
    hostname: String,
    existing: List<Meta> = emptyList(),
    mainFrame: Boolean = true,
  ): Result {
    val cx = Context.enter()
    try {
      cx.languageVersion = Context.VERSION_ES6
      val scope = cx.initStandardObjects()
      ScriptableObject.putProperty(scope, "HOST", hostname)
      ScriptableObject.putProperty(scope, "MAIN_FRAME", mainFrame)
      cx.evaluateString(
        scope,
        """
        var metas = [];
        var head = { appendChild: function(el) { metas.push({ name: el.name, content: el.content }); } };
        var document = {
          head: head,
          documentElement: head,
          createElement: function() {
            return { name: '', content: '', setAttribute: function(k, v) { this[k] = v; } };
          },
          querySelector: function(selector) {
            var match = /meta\[name="([^"]+)"\]/.exec(selector);
            if (!match) return null;
            for (var i = 0; i < metas.length; i++) {
              if (metas[i].name === match[1]) return metas[i];
            }
            return null;
          }
        };
        var window = { location: { hostname: HOST } };
        window.top = MAIN_FRAME ? window : {};
        """
          .trimIndent(),
        "harness",
        1,
        null,
      )
      for (meta in existing) {
        cx.evaluateString(
          scope,
          "metas.push({ name: ${WebViewTracePropagationScript.toJsStringLiteral(meta.name)}, " +
            "content: ${WebViewTracePropagationScript.toJsStringLiteral(meta.content)} });",
          "existing",
          1,
          null,
        )
      }

      cx.evaluateString(scope, script, "injection", 1, null)

      val count = Context.toNumber(cx.evaluateString(scope, "metas.length", "count", 1, null))
      val metas =
        (0 until count.toInt()).map { i ->
          Meta(
            Context.toString(cx.evaluateString(scope, "metas[$i].name", "name", 1, null)),
            Context.toString(cx.evaluateString(scope, "metas[$i].content", "content", 1, null)),
          )
        }
      val pwned =
        Context.toBoolean(
          cx.evaluateString(
            scope,
            "window.__pwned === 1 || typeof __pwned !== 'undefined'",
            "pwned",
            1,
            null,
          )
        )
      return Result(metas, pwned)
    } finally {
      Context.exit()
    }
  }

  private fun script(
    allowedHosts: List<String> = listOf("example.com"),
    sentryTrace: String = SENTRY_TRACE,
    baggage: String? = BAGGAGE,
  ): String =
    WebViewTracePropagationScript.build(
      WebViewTracePropagationScript.normalizeHosts(allowedHosts),
      sentryTrace,
      baggage,
    )

  @Test
  fun `injects sentry-trace and baggage meta tags on an allowed host`() {
    val result = runInjection(script(), "example.com")

    assertThat(result.metas)
      .containsExactly(Meta("sentry-trace", SENTRY_TRACE), Meta("baggage", BAGGAGE))
      .inOrder()
  }

  @Test
  fun `injects on a subdomain of an allowed host`() {
    assertThat(runInjection(script(), "app.example.com").metas).hasSize(2)
  }

  @Test
  fun `matches allowed hosts case-insensitively`() {
    assertThat(runInjection(script(listOf("Example.COM")), "app.example.com").metas).hasSize(2)
  }

  @Test
  fun `does not inject on a host that is not allowed`() {
    assertThat(runInjection(script(), "evil.com").metas).isEmpty()
  }

  @Test
  fun `does not inject on a host that merely ends with an allowed host`() {
    assertThat(runInjection(script(), "notexample.com").metas).isEmpty()
  }

  @Test
  fun `does not inject into iframes`() {
    assertThat(runInjection(script(), "example.com", mainFrame = false).metas).isEmpty()
  }

  @Test
  fun `does not inject again when the page already has a sentry-trace meta tag`() {
    val existing = listOf(Meta("sentry-trace", "already-there"))

    val result = runInjection(script(), "example.com", existing)

    assertThat(result.metas).containsExactly(Meta("sentry-trace", "already-there"))
  }

  @Test
  fun `injects only sentry-trace when there is no baggage`() {
    val result = runInjection(script(baggage = null), "example.com")

    assertThat(result.metas).containsExactly(Meta("sentry-trace", SENTRY_TRACE))
  }

  @Test
  fun `normalizes allowedHosts entries`() {
    for (entry in
      listOf(
        "  example.com  ",
        "https://example.com",
        "example.com:8080",
        "example.com/some/path",
        "example.com.",
        "https://Example.com:443/x?y#z",
      )) {
      assertThat(WebViewTracePropagationScript.normalizeHost(entry)).isEqualTo("example.com")
      assertThat(runInjection(script(listOf(entry)), "example.com").metas).hasSize(2)
    }
  }

  @Test
  fun `normalizes a subdomain wildcard entry to the bare host`() {
    for (entry in listOf("*.example.com", "https://*.Example.com/x", "*.*.example.com")) {
      assertThat(WebViewTracePropagationScript.normalizeHost(entry)).isEqualTo("example.com")
    }
    assertThat(runInjection(script(listOf("*.example.com")), "app.example.com").metas).hasSize(2)
    assertThat(runInjection(script(listOf("*.example.com")), "example.com").metas).hasSize(2)
  }

  @Test
  fun `normalizes IPv6 allowedHosts entries to the bracketed hostname`() {
    for ((entry, hostname) in
      listOf(
        "::1" to "[::1]",
        "[::1]" to "[::1]",
        "[::1]:8080" to "[::1]",
        "2001:db8::1" to "[2001:db8::1]",
        "[2001:DB8::1]" to "[2001:db8::1]",
      )) {
      assertThat(WebViewTracePropagationScript.normalizeHost(entry)).isEqualTo(hostname)
      assertThat(runInjection(script(listOf(entry)), hostname).metas).hasSize(2)
    }
  }

  @Test
  fun `normalizeHosts drops blank entries and duplicates`() {
    val hosts =
      WebViewTracePropagationScript.normalizeHosts(
        listOf("   ", "", "example.com", "https://EXAMPLE.com/", "other.com")
      )

    assertThat(hosts).containsExactly("example.com", "other.com").inOrder()
  }

  @Test
  fun `originRules allow each host and its subdomains over https`() {
    val rules = WebViewTracePropagationScript.originRules(listOf("example.com", "other.com"))

    assertThat(rules)
      .containsExactly(
        "https://example.com",
        "https://*.example.com",
        "https://other.com",
        "https://*.other.com",
      )
  }

  @Test
  fun `originRules do not add a subdomain wildcard to IP literals`() {
    val rules = WebViewTracePropagationScript.originRules(listOf("[::1]", "10.0.2.2"))

    assertThat(rules).containsExactly("https://[::1]", "https://10.0.2.2")
  }

  @Test
  fun `stripTransactionFromBaggage removes sentry-transaction and keeps every other entry`() {
    val baggage =
      "sentry-trace_id=d4cda95b652f4a1592b449d5929fda1b,sentry-environment=prod," +
        "sentry-transaction=GET%20%2Fusers%2F123,sentry-sample_rate=1.0,othervendor=keepme"

    assertThat(WebViewTracePropagationScript.stripTransactionFromBaggage(baggage))
      .isEqualTo(
        "sentry-trace_id=d4cda95b652f4a1592b449d5929fda1b,sentry-environment=prod," +
          "sentry-sample_rate=1.0,othervendor=keepme"
      )
  }

  @Test
  fun `stripTransactionFromBaggage returns null when only sentry-transaction is left`() {
    assertThat(
        WebViewTracePropagationScript.stripTransactionFromBaggage(
          "sentry-transaction=GET%20%2Fusers%2F123"
        )
      )
      .isNull()
    assertThat(WebViewTracePropagationScript.stripTransactionFromBaggage(null)).isNull()
  }

  @Test
  fun `neutralizes breakout payloads in the sentry-trace value`() {
    for (payload in BREAKOUT_PAYLOADS) {
      val result = runInjection(script(sentryTrace = payload), "example.com")

      assertThat(result.pwned).isFalse()
      assertThat(result.metas).contains(Meta("sentry-trace", payload))
    }
  }

  @Test
  fun `neutralizes breakout payloads in the baggage value`() {
    for (payload in BREAKOUT_PAYLOADS) {
      val result = runInjection(script(baggage = payload), "example.com")

      assertThat(result.pwned).isFalse()
      assertThat(result.metas).contains(Meta("baggage", payload))
    }
  }

  @Test
  fun `neutralizes breakout payloads in allowedHosts entries`() {
    for (payload in BREAKOUT_PAYLOADS) {
      val script =
        WebViewTracePropagationScript.build(listOf(payload, "example.com"), SENTRY_TRACE, BAGGAGE)

      val result = runInjection(script, "example.com")

      assertThat(result.pwned).isFalse()
      assertThat(result.metas).hasSize(2)
    }
  }

  @Test
  fun `preserves random sentry-trace values as inert content`() {
    val chars = "\"'`\\\${}();/<>\n\r\t  abc0= ,"
    val random = java.util.Random(123456789)
    repeat(200) {
      val payload = buildString {
        repeat(1 + random.nextInt(40)) { append(chars[random.nextInt(chars.length)]) }
      }

      val result = runInjection(script(sentryTrace = payload), "example.com")

      assertThat(result.pwned).isFalse()
      assertThat(result.metas).contains(Meta("sentry-trace", payload))
    }
  }

  companion object {
    private const val SENTRY_TRACE = "d4cda95b652f4a1592b449d5929fda1b-6e0c63257de34c92-1"
    private const val BAGGAGE =
      "sentry-trace_id=d4cda95b652f4a1592b449d5929fda1b,sentry-environment=prod"

    private val BREAKOUT_PAYLOADS =
      listOf(
        "\");window.__pwned=1;(\"",
        "');window.__pwned=1;('",
        "`;window.__pwned=1;`",
        "\${window.__pwned=1}",
        "</script><script>window.__pwned=1;</script>",
        "\\\";window.__pwned=1;//",
        "\"}]);window.__pwned=1;//",
        "\nwindow.__pwned=1;\n",
        "\rwindow.__pwned=1;\r",
        " window.__pwned=1;",
        " window.__pwned=1;",
        "abc\u0000;window.__pwned=1;",
      )
  }
}
