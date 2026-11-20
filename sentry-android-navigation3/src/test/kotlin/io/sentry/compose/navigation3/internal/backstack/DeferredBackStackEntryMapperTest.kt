package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import kotlin.test.assertFailsWith
import org.junit.Test

class DeferredBackStackEntryMapperTest {

  @Test
  fun `doesn't create the delegate mapper before it maps an entry`() {
    val fixture = Fixture.create()

    assertThat(fixture.creations).isEqualTo(0)
  }

  // map()

  @Test
  fun `map() - creates the delegate mapper on the first call and returns its result for the entry`() {
    val fixture = Fixture.create()
    val deferredMapper = fixture.deferredMapper

    val mapped = deferredMapper.map("home")

    assertThat(mapped).isEqualTo(SentryBackStackEntry("mapped home"))
    assertThat(fixture.creations).isEqualTo(1)
  }

  @Test
  fun `map() - reuses the created delegate mapper for later entries`() {
    val fixture = Fixture.create()
    val deferredMapper = fixture.deferredMapper
    deferredMapper.map("home")

    val mapped = listOf("detail", "settings").map(deferredMapper::map)

    assertThat(mapped)
      .containsExactly(
        SentryBackStackEntry("mapped detail"),
        SentryBackStackEntry("mapped settings"),
      )
      .inOrder()
    assertThat(fixture.creations).isEqualTo(1)
  }

  @Test
  fun `map() - propagates the exception thrown while creating the delegate mapper`() {
    val exception = IllegalStateException("creation failed")
    val fixture = Fixture.create(creationFailure = exception)
    val deferredMapper = fixture.deferredMapper

    val thrown = assertFailsWith<IllegalStateException> { deferredMapper.map("home") }

    assertThat(thrown).isSameInstanceAs(exception)
  }

  @Test
  fun `map() - creates the delegate mapper again after creating it threw`() {
    val fixture = Fixture.create(creationFailure = IllegalStateException("creation failed"))
    val deferredMapper = fixture.deferredMapper
    assertFailsWith<IllegalStateException> { deferredMapper.map("home") }
    fixture.creationFailure = null

    val mapped = deferredMapper.map("home")

    assertThat(mapped).isEqualTo(SentryBackStackEntry("mapped home"))
    assertThat(fixture.creations).isEqualTo(2)
  }

  private class Fixture private constructor(var creationFailure: RuntimeException?) {
    var creations = 0
      private set

    private val delegateMapper = BackStackEntryMapper<Any> { SentryBackStackEntry("mapped $it") }

    val deferredMapper = DeferredBackStackEntryMapper {
      creations++
      creationFailure?.let { throw it }
      delegateMapper
    }

    companion object {
      fun create(creationFailure: RuntimeException? = null) = Fixture(creationFailure)
    }
  }
}
