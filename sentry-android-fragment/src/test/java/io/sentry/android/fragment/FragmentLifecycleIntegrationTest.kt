package io.sentry.android.fragment

import android.app.Activity
import android.app.Application
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import io.sentry.IScopes
import io.sentry.SentryOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mockito.kotlin.check
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FragmentLifecycleIntegrationTest {
  private class Fixture {
    val application = mock<Application>()
    val fragmentManager = mock<FragmentManager>()
    val fragmentActivity =
      mock<FragmentActivity> { on { supportFragmentManager } doReturn fragmentManager }
    val scopes = mock<IScopes>()
    val options = SentryOptions()

    fun getSut(
      enableFragmentLifecycleBreadcrumbs: Boolean = true,
      enableAutoFragmentLifecycleTracing: Boolean = false,
    ): FragmentLifecycleIntegration {
      whenever(scopes.options).thenReturn(options)
      return FragmentLifecycleIntegration(
        application = application,
        enableFragmentLifecycleBreadcrumbs = enableFragmentLifecycleBreadcrumbs,
        enableAutoFragmentLifecycleTracing = enableAutoFragmentLifecycleTracing,
      )
    }
  }

  private val fixture = Fixture()

  @Test
  fun `When register, it should register activity lifecycle callbacks`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)

    verify(fixture.application).registerActivityLifecycleCallbacks(sut)
  }

  @Test
  fun `When close, it should unregister lifecycle callbacks`() {
    val sut = fixture.getSut()

    sut.register(fixture.scopes, fixture.options)
    sut.close()

    verify(fixture.application).unregisterActivityLifecycleCallbacks(sut)
  }

  @Test
  fun `When FragmentActivity is created, it should register fragment lifecycle callbacks`() {
    val sut = fixture.getSut()
    val fragmentManager = mock<FragmentManager>()
    val fragmentActivity =
      mock<FragmentActivity> { on { supportFragmentManager } doReturn fragmentManager }

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(fragmentActivity, savedInstanceState = null)

    verify(fragmentManager)
      .registerFragmentLifecycleCallbacks(
        check { fragmentCallbacks -> fragmentCallbacks is SentryFragmentLifecycleCallbacks },
        eq(true),
      )
  }

  @Test
  fun `When FragmentActivity is created, it should register fragment lifecycle callbacks with passed config`() {
    val sut =
      fixture.getSut(
        enableFragmentLifecycleBreadcrumbs = false,
        enableAutoFragmentLifecycleTracing = true,
      )

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(fixture.fragmentActivity, savedInstanceState = null)

    verify(fixture.fragmentManager)
      .registerFragmentLifecycleCallbacks(
        check { fragmentCallbacks ->
          val callback = (fragmentCallbacks as SentryFragmentLifecycleCallbacks)
          assertTrue(callback.enableAutoFragmentLifecycleTracing)
          assertEquals(emptySet(), callback.filterFragmentLifecycleBreadcrumbs)
        },
        eq(true),
      )
  }

  @Test
  fun `When not a FragmentActivity is created, it should not crash`() {
    val sut = fixture.getSut()
    val activity = mock<Activity>()

    sut.register(fixture.scopes, fixture.options)
    sut.onActivityCreated(activity, savedInstanceState = null)
  }

  @Test
  fun `When close is called without register, it should not crash`() {
    val sut = fixture.getSut()

    sut.close()
  }
}
