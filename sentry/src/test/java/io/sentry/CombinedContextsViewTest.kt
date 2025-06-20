package io.sentry

import io.sentry.protocol.App
import io.sentry.protocol.Browser
import io.sentry.protocol.Contexts
import io.sentry.protocol.Device
import io.sentry.protocol.Gpu
import io.sentry.protocol.OperatingSystem
import io.sentry.protocol.Response
import io.sentry.protocol.SentryRuntime
import io.sentry.protocol.Spring
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CombinedContextsViewTest {
  private class Fixture {
    lateinit var current: Contexts
    lateinit var isolation: Contexts
    lateinit var global: Contexts

    fun getSut(): CombinedContextsView {
      current = Contexts()
      isolation = Contexts()
      global = Contexts()

      return CombinedContextsView(global, isolation, current, ScopeType.ISOLATION)
    }
  }

  private val fixture = Fixture()

  @Test
  fun `uses default context CURRENT`() {
    fixture.getSut()
    val combined =
      CombinedContextsView(fixture.global, fixture.isolation, fixture.current, ScopeType.CURRENT)
    combined.setTrace(SpanContext("some"))
    assertEquals("some", fixture.current.trace?.op)
  }

  @Test
  fun `uses default context ISOLATION`() {
    fixture.getSut()
    val combined =
      CombinedContextsView(fixture.global, fixture.isolation, fixture.current, ScopeType.ISOLATION)
    combined.setTrace(SpanContext("some"))
    assertEquals("some", fixture.isolation.trace?.op)
  }

  @Test
  fun `uses default context GLOBAL`() {
    fixture.getSut()
    val combined =
      CombinedContextsView(fixture.global, fixture.isolation, fixture.current, ScopeType.GLOBAL)
    combined.setTrace(SpanContext("some"))
    assertEquals("some", fixture.global.trace?.op)
  }

  @Test
  fun `prefers trace from current context`() {
    val combined = fixture.getSut()
    fixture.current.setTrace(SpanContext("current"))
    fixture.isolation.setTrace(SpanContext("isolation"))
    fixture.global.setTrace(SpanContext("global"))

    assertEquals("current", combined.trace?.op)
  }

  @Test
  fun `uses isolation trace if current context does not have it`() {
    val combined = fixture.getSut()
    fixture.isolation.setTrace(SpanContext("isolation"))
    fixture.global.setTrace(SpanContext("global"))

    assertEquals("isolation", combined.trace?.op)
  }

  @Test
  fun `uses global trace if current and isolation context do not have it`() {
    val combined = fixture.getSut()
    fixture.global.setTrace(SpanContext("global"))

    assertEquals("global", combined.trace?.op)
  }

  @Test
  fun `sets trace on default context`() {
    val combined = fixture.getSut()
    combined.setTrace(SpanContext("some"))

    assertNull(fixture.current.trace)
    assertEquals("some", fixture.isolation.trace?.op)
    assertNull(fixture.global.trace)
  }

  @Test
  fun `prefers app from current context`() {
    val combined = fixture.getSut()
    fixture.current.setApp(App().also { it.appName = "current" })
    fixture.isolation.setApp(App().also { it.appName = "isolation" })
    fixture.global.setApp(App().also { it.appName = "global" })

    assertEquals("current", combined.app?.appName)
  }

  @Test
  fun `uses isolation app if current context does not have it`() {
    val combined = fixture.getSut()
    fixture.isolation.setApp(App().also { it.appName = "isolation" })
    fixture.global.setApp(App().also { it.appName = "global" })

    assertEquals("isolation", combined.app?.appName)
  }

  @Test
  fun `uses global app if current and isolation context do not have it`() {
    val combined = fixture.getSut()
    fixture.global.setApp(App().also { it.appName = "global" })

    assertEquals("global", combined.app?.appName)
  }

  @Test
  fun `sets app on default context`() {
    val combined = fixture.getSut()
    combined.setApp(App().also { it.appName = "some" })

    assertNull(fixture.current.app)
    assertEquals("some", fixture.isolation.app?.appName)
    assertNull(fixture.global.app)
  }

  @Test
  fun `prefers browser from current context`() {
    val combined = fixture.getSut()
    fixture.current.setBrowser(Browser().also { it.name = "current" })
    fixture.isolation.setBrowser(Browser().also { it.name = "isolation" })
    fixture.global.setBrowser(Browser().also { it.name = "global" })

    assertEquals("current", combined.browser?.name)
  }

  @Test
  fun `uses isolation browser if current context does not have it`() {
    val combined = fixture.getSut()
    fixture.isolation.setBrowser(Browser().also { it.name = "isolation" })
    fixture.global.setBrowser(Browser().also { it.name = "global" })

    assertEquals("isolation", combined.browser?.name)
  }

  @Test
  fun `uses global browser if current and isolation context do not have it`() {
    val combined = fixture.getSut()
    fixture.global.setBrowser(Browser().also { it.name = "global" })

    assertEquals("global", combined.browser?.name)
  }

  @Test
  fun `sets browser on default context`() {
    val combined = fixture.getSut()
    combined.setBrowser(Browser().also { it.name = "some" })

    assertNull(fixture.current.browser)
    assertEquals("some", fixture.isolation.browser?.name)
    assertNull(fixture.global.browser)
  }

  @Test
  fun `prefers device from current context`() {
    val combined = fixture.getSut()
    fixture.current.setDevice(Device().also { it.name = "current" })
    fixture.isolation.setDevice(Device().also { it.name = "isolation" })
    fixture.global.setDevice(Device().also { it.name = "global" })

    assertEquals("current", combined.device?.name)
  }

  @Test
  fun `uses isolation device if current context does not have it`() {
    val combined = fixture.getSut()
    fixture.isolation.setDevice(Device().also { it.name = "isolation" })
    fixture.global.setDevice(Device().also { it.name = "global" })

    assertEquals("isolation", combined.device?.name)
  }

  @Test
  fun `uses global device if current and isolation context do not have it`() {
    val combined = fixture.getSut()
    fixture.global.setDevice(Device().also { it.name = "global" })

    assertEquals("global", combined.device?.name)
  }

  @Test
  fun `sets device on default context`() {
    val combined = fixture.getSut()
    combined.setDevice(Device().also { it.name = "some" })

    assertNull(fixture.current.device)
    assertEquals("some", fixture.isolation.device?.name)
    assertNull(fixture.global.device)
  }

  @Test
  fun `prefers operatingSystem from current context`() {
    val combined = fixture.getSut()
    fixture.current.setOperatingSystem(OperatingSystem().also { it.name = "current" })
    fixture.isolation.setOperatingSystem(OperatingSystem().also { it.name = "isolation" })
    fixture.global.setOperatingSystem(OperatingSystem().also { it.name = "global" })

    assertEquals("current", combined.operatingSystem?.name)
  }

  @Test
  fun `uses isolation operatingSystem if current context does not have it`() {
    val combined = fixture.getSut()
    fixture.isolation.setOperatingSystem(OperatingSystem().also { it.name = "isolation" })
    fixture.global.setOperatingSystem(OperatingSystem().also { it.name = "global" })

    assertEquals("isolation", combined.operatingSystem?.name)
  }

  @Test
  fun `uses global operatingSystem if current and isolation context do not have it`() {
    val combined = fixture.getSut()
    fixture.global.setOperatingSystem(OperatingSystem().also { it.name = "global" })

    assertEquals("global", combined.operatingSystem?.name)
  }

  @Test
  fun `sets operatingSystem on default context`() {
    val combined = fixture.getSut()
    combined.setOperatingSystem(OperatingSystem().also { it.name = "some" })

    assertNull(fixture.current.operatingSystem)
    assertEquals("some", fixture.isolation.operatingSystem?.name)
    assertNull(fixture.global.operatingSystem)
  }

  @Test
  fun `prefers runtime from current context`() {
    val combined = fixture.getSut()
    fixture.current.setRuntime(SentryRuntime().also { it.name = "current" })
    fixture.isolation.setRuntime(SentryRuntime().also { it.name = "isolation" })
    fixture.global.setRuntime(SentryRuntime().also { it.name = "global" })

    assertEquals("current", combined.runtime?.name)
  }

  @Test
  fun `uses isolation runtime if current context does not have it`() {
    val combined = fixture.getSut()
    fixture.isolation.setRuntime(SentryRuntime().also { it.name = "isolation" })
    fixture.global.setRuntime(SentryRuntime().also { it.name = "global" })

    assertEquals("isolation", combined.runtime?.name)
  }

  @Test
  fun `uses global runtime if current and isolation context do not have it`() {
    val combined = fixture.getSut()
    fixture.global.setRuntime(SentryRuntime().also { it.name = "global" })

    assertEquals("global", combined.runtime?.name)
  }

  @Test
  fun `sets runtime on default context`() {
    val combined = fixture.getSut()
    combined.setRuntime(SentryRuntime().also { it.name = "some" })

    assertNull(fixture.current.runtime)
    assertEquals("some", fixture.isolation.runtime?.name)
    assertNull(fixture.global.runtime)
  }

  @Test
  fun `prefers gpu from current context`() {
    val combined = fixture.getSut()
    fixture.current.setGpu(Gpu().also { it.name = "current" })
    fixture.isolation.setGpu(Gpu().also { it.name = "isolation" })
    fixture.global.setGpu(Gpu().also { it.name = "global" })

    assertEquals("current", combined.gpu?.name)
  }

  @Test
  fun `uses isolation gpu if current context does not have it`() {
    val combined = fixture.getSut()
    fixture.isolation.setGpu(Gpu().also { it.name = "isolation" })
    fixture.global.setGpu(Gpu().also { it.name = "global" })

    assertEquals("isolation", combined.gpu?.name)
  }

  @Test
  fun `uses global gpu if current and isolation context do not have it`() {
    val combined = fixture.getSut()
    fixture.global.setGpu(Gpu().also { it.name = "global" })

    assertEquals("global", combined.gpu?.name)
  }

  @Test
  fun `sets gpu on default context`() {
    val combined = fixture.getSut()
    combined.setGpu(Gpu().also { it.name = "some" })

    assertNull(fixture.current.gpu)
    assertEquals("some", fixture.isolation.gpu?.name)
    assertNull(fixture.global.gpu)
  }

  @Test
  fun `prefers response from current context`() {
    val combined = fixture.getSut()
    fixture.current.setResponse(Response().also { it.cookies = "current" })
    fixture.isolation.setResponse(Response().also { it.cookies = "isolation" })
    fixture.global.setResponse(Response().also { it.cookies = "global" })

    assertEquals("current", combined.response?.cookies)
  }

  @Test
  fun `uses isolation response if current context does not have it`() {
    val combined = fixture.getSut()
    fixture.isolation.setResponse(Response().also { it.cookies = "isolation" })
    fixture.global.setResponse(Response().also { it.cookies = "global" })

    assertEquals("isolation", combined.response?.cookies)
  }

  @Test
  fun `uses global response if current and isolation context do not have it`() {
    val combined = fixture.getSut()
    fixture.global.setResponse(Response().also { it.cookies = "global" })

    assertEquals("global", combined.response?.cookies)
  }

  @Test
  fun `sets response on default context`() {
    val combined = fixture.getSut()
    combined.setResponse(Response().also { it.cookies = "some" })

    assertNull(fixture.current.response)
    assertEquals("some", fixture.isolation.response?.cookies)
    assertNull(fixture.global.response)
  }

  @Test
  fun `withResponse is executed on current if present`() {
    val combined = fixture.getSut()
    fixture.current.setResponse(Response().also { it.cookies = "current" })
    fixture.isolation.setResponse(Response().also { it.cookies = "isolation" })
    fixture.global.setResponse(Response().also { it.cookies = "global" })

    combined.withResponse { response -> response.cookies = "updated" }

    assertEquals("updated", fixture.current.response?.cookies)
    assertEquals("isolation", fixture.isolation.response?.cookies)
    assertEquals("global", fixture.global.response?.cookies)
  }

  @Test
  fun `withResponse is executed on isolation if current not present`() {
    val combined = fixture.getSut()
    fixture.isolation.setResponse(Response().also { it.cookies = "isolation" })
    fixture.global.setResponse(Response().also { it.cookies = "global" })

    combined.withResponse { response -> response.cookies = "updated" }

    assertNull(fixture.current.response)
    assertEquals("updated", fixture.isolation.response?.cookies)
    assertEquals("global", fixture.global.response?.cookies)
  }

  @Test
  fun `withResponse is executed on global if current and isoaltion not present`() {
    val combined = fixture.getSut()
    fixture.global.setResponse(Response().also { it.cookies = "global" })

    combined.withResponse { response -> response.cookies = "updated" }

    assertNull(fixture.current.response)
    assertNull(fixture.isolation.response)
    assertEquals("updated", fixture.global.response?.cookies)
  }

  @Test
  fun `withResponse is executed on default if not present anywhere`() {
    val combined = fixture.getSut()

    combined.withResponse { response -> response.cookies = "updated" }

    assertNull(fixture.current.response)
    assertEquals("updated", fixture.isolation.response?.cookies)
    assertNull(fixture.global.response)
  }

  @Test
  fun `prefers spring from current context`() {
    val combined = fixture.getSut()
    fixture.current.setSpring(
      Spring().also { it.activeProfiles = listOf("current").toTypedArray() }
    )
    fixture.isolation.setSpring(
      Spring().also { it.activeProfiles = listOf("isolation").toTypedArray() }
    )
    fixture.global.setSpring(Spring().also { it.activeProfiles = listOf("global").toTypedArray() })

    assertContentEquals(listOf("current").toTypedArray(), combined.spring?.activeProfiles)
  }

  @Test
  fun `uses isolation spring if current context does not have it`() {
    val combined = fixture.getSut()
    fixture.isolation.setSpring(
      Spring().also { it.activeProfiles = listOf("isolation").toTypedArray() }
    )
    fixture.global.setSpring(Spring().also { it.activeProfiles = listOf("global").toTypedArray() })

    assertContentEquals(listOf("isolation").toTypedArray(), combined.spring?.activeProfiles)
  }

  @Test
  fun `uses global spring if current and isolation context do not have it`() {
    val combined = fixture.getSut()
    fixture.global.setSpring(Spring().also { it.activeProfiles = listOf("global").toTypedArray() })

    assertContentEquals(listOf("global").toTypedArray(), combined.spring?.activeProfiles)
  }

  @Test
  fun `sets spring on default context`() {
    val combined = fixture.getSut()
    combined.setSpring(Spring().also { it.activeProfiles = listOf("test").toTypedArray() })

    assertNull(fixture.current.spring)
    assertContentEquals(listOf("test").toTypedArray(), combined.spring?.activeProfiles)
    assertNull(fixture.global.spring)
  }

  @Test
  fun `size combines contexts`() {
    val combined = fixture.getSut()
    fixture.current.setTrace(SpanContext("current"))
    fixture.isolation.setApp(App().also { it.appName = "isolation" })
    fixture.global.setGpu(Gpu().also { it.name = "global" })

    assertEquals(3, combined.size)
  }

  @Test
  fun `size considers overrides`() {
    val combined = fixture.getSut()
    fixture.current.setTrace(SpanContext("current"))
    fixture.isolation.setTrace(SpanContext("isolation"))
    fixture.global.setTrace(SpanContext("global"))

    assertEquals(1, combined.size)
  }

  @Test
  fun `isEmpty`() {
    val combined = fixture.getSut()
    assertTrue(combined.isEmpty)
  }

  @Test
  fun `isNotEmpty if current has value`() {
    val combined = fixture.getSut()
    fixture.current.setTrace(SpanContext("current"))

    assertFalse(combined.isEmpty)
  }

  @Test
  fun `isNotEmpty if isolation has value`() {
    val combined = fixture.getSut()
    fixture.isolation.setApp(App().also { it.appName = "isolation" })

    assertFalse(combined.isEmpty)
  }

  @Test
  fun `isNotEmpty if global has value`() {
    val combined = fixture.getSut()
    fixture.global.setGpu(Gpu().also { it.name = "global" })

    assertFalse(combined.isEmpty)
  }

  @Test
  fun `containsKey false`() {
    val combined = fixture.getSut()
    assertFalse(combined.containsKey("trace"))
  }

  @Test
  fun `containsKey current`() {
    val combined = fixture.getSut()
    fixture.current.setTrace(SpanContext("current"))
    assertTrue(combined.containsKey("trace"))
  }

  @Test
  fun `containsKey isolation`() {
    val combined = fixture.getSut()
    fixture.isolation.setTrace(SpanContext("isolation"))
    assertTrue(combined.containsKey("trace"))
  }

  @Test
  fun `containsKey global`() {
    val combined = fixture.getSut()
    fixture.global.setTrace(SpanContext("global"))
    assertTrue(combined.containsKey("trace"))
  }

  @Test
  fun `keys combines contexts`() {
    val combined = fixture.getSut()
    fixture.current.setTrace(SpanContext("current"))
    fixture.isolation.setApp(App().also { it.appName = "isolation" })
    fixture.global.setGpu(Gpu().also { it.name = "global" })

    assertEquals(listOf("app", "gpu", "trace"), combined.keys().toList().sorted())
  }

  @Test
  fun `entrySet combines contexts`() {
    val combined = fixture.getSut()
    val trace = SpanContext("current")
    fixture.current.setTrace(trace)
    val app = App().also { it.appName = "isolation" }
    fixture.isolation.setApp(app)
    val gpu = Gpu().also { it.name = "global" }
    fixture.global.setGpu(gpu)

    val entrySet = combined.entrySet()
    assertEquals(3, entrySet.size)
    assertNotNull(entrySet.firstOrNull { it.key == "trace" && it.value == trace })
    assertNotNull(entrySet.firstOrNull { it.key == "app" && it.value == app })
    assertNotNull(entrySet.firstOrNull { it.key == "gpu" && it.value == gpu })
  }

  @Test
  fun `get prefers current`() {
    val combined = fixture.getSut()
    fixture.current.put("test", "current")
    fixture.isolation.put("test", "isolation")
    fixture.global.put("test", "global")

    assertEquals("current", combined.get("test"))
  }

  @Test
  fun `get uses isolation if not in current`() {
    val combined = fixture.getSut()
    fixture.isolation.put("test", "isolation")
    fixture.global.put("test", "global")

    assertEquals("isolation", combined.get("test"))
  }

  @Test
  fun `get uses global if not in current or isolation`() {
    val combined = fixture.getSut()
    fixture.global.put("test", "global")

    assertEquals("global", combined.get("test"))
  }

  @Test
  fun `put stores in default context`() {
    val combined = fixture.getSut()
    combined.put("test", "aValue")

    assertNull(fixture.current.get("test"))
    assertEquals("aValue", fixture.isolation.get("test"))
    assertNull(fixture.global.get("test"))
  }

  @Test
  fun `remove removes from default context`() {
    val combined = fixture.getSut()
    fixture.current.put("test", "current")
    fixture.isolation.put("test", "isolation")
    fixture.global.put("test", "global")

    combined.remove("test")

    assertEquals("current", fixture.current.get("test"))
    assertNull(fixture.isolation.get("test"))
    assertEquals("global", fixture.global.get("test"))
  }

  @Test
  fun `set null value on context does not cause exception`() {
    val combined = fixture.getSut()
    combined.set("k", null)
    assertFalse(combined.containsKey("k"))
  }

  @Test
  fun `set null key on context does not cause exception`() {
    val combined = fixture.getSut()
    combined.set(null, "v")
    assertFalse(combined.containsKey(null))
  }

  @Test
  fun `set null key and value on context does not cause exception`() {
    val combined = fixture.getSut()
    combined.set(null, null)
    assertFalse(combined.containsKey(null))
  }

  @Test
  fun `remove null key from context does not cause exception`() {
    val combined = fixture.getSut()
    combined.remove(null)
  }
}
