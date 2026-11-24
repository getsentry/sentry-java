package io.sentry.compose.navigation3.internal.backstack

import com.google.common.truth.Truth.assertThat
import io.sentry.ILogger
import io.sentry.Sentry
import io.sentry.SentryIntegrationPackageStorage
import io.sentry.SentryLevel
import io.sentry.compose.navigation3.BackStackEntryMapper
import io.sentry.compose.navigation3.SentryBackStackEntry
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxBackStackEntryMapper
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.INCOMPATIBLE_KOTLINX_ADVICE
import io.sentry.compose.navigation3.internal.backstack.kotlinx.KotlinxMapperDiagnostics.Companion.MIN_KOTLINX_SERIALIZATION_VERSION
import io.sentry.test.initForTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.test.assertFailsWith
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class BuiltInBackStackEntryMappersTest {

  @Serializable @SerialName("Home") data object Home

  @Serializable @SerialName("Detail") data class Detail(val id: String)

  data class NotSerializable(val id: String)

  private val kotlinxMapper = BackStackEntryMapper<Any> { SentryBackStackEntry("Kotlinx") }
  private val logger = mock<ILogger>()

  @Before
  fun clearIntegrations() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  @After
  fun clearIntegrationsAfterTest() {
    SentryIntegrationPackageStorage.getInstance().clearStorage()
  }

  // serialName and serialNameAndProperties

  @Test
  fun `serialName names entries without recording their properties`() {
    val mapper = BuiltInBackStackEntryMappers.serialName

    val mapped = listOf(Home, Detail("1")).map(mapper::map)

    assertThat(mapped)
      .containsExactly(SentryBackStackEntry("Home"), SentryBackStackEntry("Detail"))
      .inOrder()
  }

  @Test
  fun `serialNameAndProperties records properties as arguments`() {
    val mapper = BuiltInBackStackEntryMappers.serialNameAndProperties

    val mapped = listOf(Home, Detail("1")).map(mapper::map)

    assertThat(mapped)
      .containsExactly(
        SentryBackStackEntry("Home"),
        SentryBackStackEntry("Detail", mapOf("id" to "1")),
      )
      .inOrder()
  }

  @Test
  fun `serialName and serialNameAndProperties return the same mapper on every access`() {
    assertThat(BuiltInBackStackEntryMappers.serialName)
      .isSameInstanceAs(BuiltInBackStackEntryMappers.serialName)
    assertThat(BuiltInBackStackEntryMappers.serialNameAndProperties)
      .isSameInstanceAs(BuiltInBackStackEntryMappers.serialNameAndProperties)
  }

  // BackStackEntryMapper's companion mappers

  @Test
  fun `BackStackEntryMapper SerialName and SerialNameAndProperties return the built-in mappers`() {
    assertThat(BackStackEntryMapper.SerialName)
      .isSameInstanceAs(BuiltInBackStackEntryMappers.serialName)
    assertThat(BackStackEntryMapper.SerialNameAndProperties)
      .isSameInstanceAs(BuiltInBackStackEntryMappers.serialNameAndProperties)
  }

  @Test
  fun `BackStackEntryMapper SerialName can serve as the mapper for any entry type`() {
    val homeMapper: BackStackEntryMapper<Home> = BackStackEntryMapper.SerialName

    assertThat(homeMapper.map(Home)).isEqualTo(SentryBackStackEntry("Home"))
  }

  @Test
  fun `BackStackEntryMapper SerialName maps entries to null when the kotlinx mapper can't be loaded`() {
    val classLoader = KotlinxMapperHidingClassLoader()

    val mapper = classLoader.companionMapper("getSerialName")

    assertThat(mapper.map(Home)).isNull()
    assertThat(classLoader.wasKotlinxMapperRequested).isTrue()
  }

  @Test
  fun `BackStackEntryMapper SerialNameAndProperties maps entries to null when the kotlinx mapper can't be loaded`() {
    val classLoader = KotlinxMapperHidingClassLoader()

    val mapper = classLoader.companionMapper("getSerialNameAndProperties")

    assertThat(mapper.map(Detail("1"))).isNull()
    assertThat(classLoader.wasKotlinxMapperRequested).isTrue()
  }

  @Test
  fun `BackStackEntryMapper SerialName maps entries to null when kotlinx serialization is missing`() {
    val classLoader = KotlinxMapperHidingClassLoader(hidesKotlinx = true)
    val entry = classLoader.notSerializableEntry("1")

    val mapper = classLoader.companionMapper("getSerialName")

    assertThat(mapper.map("home")).isNull()
    assertThat(mapper.map(entry)).isNull()
    assertThat(classLoader.wasKotlinxRequested).isTrue()
  }

  @Test
  fun `BackStackEntryMapper SerialNameAndProperties maps entries to null when kotlinx serialization is missing`() {
    val classLoader = KotlinxMapperHidingClassLoader(hidesKotlinx = true)
    val entry = classLoader.notSerializableEntry("1")

    val mapper = classLoader.companionMapper("getSerialNameAndProperties")

    assertThat(mapper.map("home")).isNull()
    assertThat(mapper.map(entry)).isNull()
    assertThat(classLoader.wasKotlinxRequested).isTrue()
  }

  // First access from several threads

  @Test
  fun `BackStackEntryMapper SerialName holds no lock while creating the mapper logs`() {
    val race = raceOnFirstAccess("getSerialName")

    assertThat(race.otherThreadLoggerGotAppLock).isTrue()
  }

  @Test
  fun `BackStackEntryMapper SerialName returns the mapper published first to racing threads`() {
    val race = raceOnFirstAccess("getSerialName")

    assertThat(race.otherThreadMapper).isSameInstanceAs(race.thisThreadMapper)
    assertThat(race.laterMapper).isSameInstanceAs(race.thisThreadMapper)
  }

  @Test
  fun `BackStackEntryMapper SerialNameAndProperties holds no lock while creating the mapper logs`() {
    val race = raceOnFirstAccess("getSerialNameAndProperties")

    assertThat(race.otherThreadLoggerGotAppLock).isTrue()
  }

  @Test
  fun `BackStackEntryMapper SerialNameAndProperties returns the mapper published first to racing threads`() {
    val race = raceOnFirstAccess("getSerialNameAndProperties")

    assertThat(race.otherThreadMapper).isSameInstanceAs(race.thisThreadMapper)
    assertThat(race.laterMapper).isSameInstanceAs(race.thisThreadMapper)
  }

  // MIN_KOTLINX_SERIALIZATION_VERSION

  @Test
  fun `MIN_KOTLINX_SERIALIZATION_VERSION matches kotlinxSerializationFloor in nav3 versions catalog`() {
    val floor =
      checkNotNull(System.getProperty(KOTLINX_SERIALIZATION_FLOOR_PROPERTY)) {
        "System property $KOTLINX_SERIALIZATION_FLOOR_PROPERTY is missing. Run this test through " +
          "Gradle, which sets it from kotlinxSerializationFloor in gradle/nav3.versions.toml."
      }

    assertThat(MIN_KOTLINX_SERIALIZATION_VERSION).isEqualTo(floor)
  }

  // create()

  @Test
  fun `create() - returns the kotlinx mapper when it can be created`() {
    val mapper =
      BuiltInBackStackEntryMappers.create(logger = { logger }) {
        kotlinxMapper
      }

    assertThat(mapper).isSameInstanceAs(kotlinxMapper)
  }

  @Test
  fun `create() - doesn't log when the kotlinx mapper can be created`() {
    BuiltInBackStackEntryMappers.create(logger = { logger }) { kotlinxMapper }

    verifyNoInteractions(logger)
  }

  @Test
  fun `create() - doesn't add the mappers unavailable integration when the kotlinx mapper can be created`() {
    BuiltInBackStackEntryMappers.create(logger = { logger }) { kotlinxMapper }

    assertThat(SentryIntegrationPackageStorage.getInstance().integrations)
      .doesNotContain(BuiltInBackStackEntryMappers.MAPPERS_UNAVAILABLE_INTEGRATION)
  }

  @Test
  fun `create() - maps entries to null when creating the kotlinx mapper throws NoClassDefFoundError`() {
    val mapper =
      BuiltInBackStackEntryMappers.create(logger = { logger }) {
        throw NoClassDefFoundError("kotlinx/serialization/KSerializer")
      }

    assertThat(mapper.map(Home)).isNull()
  }

  @Test
  fun `create() - maps entries to null when creating the kotlinx mapper throws ExceptionInInitializerError`() {
    val mapper =
      BuiltInBackStackEntryMappers.create(logger = { logger }) {
        throw ExceptionInInitializerError("static initializer failed")
      }

    assertThat(mapper.map(Home)).isNull()
  }

  @Test
  fun `create() - logs the LinkageError and compatibility advice when mapper creation fails`() {
    val error = NoClassDefFoundError("kotlinx/serialization/KSerializer")

    BuiltInBackStackEntryMappers.create(logger = { logger }) { throw error }

    val message = argumentCaptor<String>()
    verify(logger, times(1)).log(eq(SentryLevel.WARNING), message.capture(), eq(error))
    assertThat(message.firstValue).contains(INCOMPATIBLE_KOTLINX_ADVICE)
  }

  @Test
  fun `create() - adds the mappers unavailable integration when creating the kotlinx mapper throws a LinkageError`() {
    BuiltInBackStackEntryMappers.create(logger = { logger }) {
      throw NoClassDefFoundError("kotlinx/serialization/KSerializer")
    }

    assertThat(SentryIntegrationPackageStorage.getInstance().integrations)
      .contains(BuiltInBackStackEntryMappers.MAPPERS_UNAVAILABLE_INTEGRATION)
  }

  @Test
  fun `create() - propagates an exception that is not a linkage error from creating the kotlinx mapper`() {
    val exception = IllegalStateException("mapper bug")

    val thrown =
      assertFailsWith<IllegalStateException> {
        BuiltInBackStackEntryMappers.create(logger = { logger }) {
          throw exception
        }
      }

    assertThat(thrown).isSameInstanceAs(exception)
  }

  /**
   * Races two threads on the first access of a fresh copy of the companion mapper [getterName], in
   * the shape of a deadlock: this thread holds a lock of the app's while it accesses the mapper,
   * and the other thread's creation of the mapper, which is already logging that the kotlinx mapper
   * can't be loaded, waits for that lock in the app's logger.
   *
   * Every wait is bounded, so a deadlock makes the logger give up on the lock rather than hang. The
   * assertions inside are precondition guards that the race ran as intended, not the contracts
   * under test.
   */
  private fun raceOnFirstAccess(getterName: String): FirstAccessRace {
    val appLock = ReentrantLock()
    val logger = LockingLogger(appLock, thisThread = Thread.currentThread())
    initForTest(
      {
        it.dsn = "https://key@sentry.io/proj"
        it.isDebug = true
        it.setLogger(logger)
      },
      true,
    )
    try {
      val classLoader = KotlinxMapperHidingClassLoader()
      appLock.lock()
      var otherThreadMapper: Any? = null
      val otherThread =
        thread(name = "other-mapper-user") {
          otherThreadMapper = classLoader.companionMapper(getterName).delegate
        }
      assertThat(logger.otherThreadLogging.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue()

      val thisThreadMapper =
        try {
          classLoader.companionMapper(getterName).delegate
        } finally {
          appLock.unlock()
        }
      otherThread.join(TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS * 2))
      assertThat(otherThread.isAlive).isFalse()

      return FirstAccessRace(
        otherThreadLoggerGotAppLock = logger.otherThreadGotLock,
        otherThreadMapper = otherThreadMapper,
        thisThreadMapper = thisThreadMapper,
        laterMapper = classLoader.companionMapper(getterName).delegate,
      )
    } finally {
      Sentry.close()
    }
  }

  private class FirstAccessRace(
    val otherThreadLoggerGotAppLock: Boolean,
    val otherThreadMapper: Any?,
    val thisThreadMapper: Any,
    val laterMapper: Any,
  )

  /**
   * An app's logger that takes [appLock] to log that the built-in mappers couldn't be created, as
   * an app's logger might, giving up after a timeout. Records whether a thread other than
   * [thisThread] got the lock.
   */
  private class LockingLogger(private val appLock: ReentrantLock, private val thisThread: Thread) :
    ILogger {
    val otherThreadLogging = CountDownLatch(1)

    @Volatile var otherThreadGotLock = false

    override fun log(level: SentryLevel, message: String, vararg args: Any?) = Unit

    override fun log(level: SentryLevel, message: String, throwable: Throwable?) {
      if (!message.startsWith(CREATION_FAILED)) return
      val isOtherThread = Thread.currentThread() != thisThread
      if (isOtherThread) otherThreadLogging.countDown()
      val gotLock = appLock.tryLock(TIMEOUT_SECONDS, TimeUnit.SECONDS)
      if (isOtherThread) otherThreadGotLock = gotLock
      if (gotLock) appLock.unlock()
    }

    override fun log(
      level: SentryLevel,
      throwable: Throwable?,
      message: String,
      vararg args: Any?,
    ) = Unit

    override fun isEnabled(level: SentryLevel?): Boolean = true
  }

  /**
   * Loads its own copy of this module's classes, so their singletons are created anew, but can't
   * find [KotlinxBackStackEntryMapper], as when it can't be linked against the app's
   * kotlinx.serialization. Other classes come from the test's class loader.
   *
   * With `hidesKotlinx = true`, it finds [KotlinxBackStackEntryMapper] but not
   * kotlinx.serialization itself, as when the app doesn't depend on it.
   */
  private class KotlinxMapperHidingClassLoader(private val hidesKotlinx: Boolean = false) :
    ClassLoader(BuiltInBackStackEntryMappersTest::class.java.classLoader) {

    @Volatile
    var wasKotlinxMapperRequested = false
      private set

    @Volatile
    var wasKotlinxRequested = false
      private set

    /** Returns a [NotSerializable] loaded by this class loader, which doesn't need kotlinx. */
    fun notSerializableEntry(id: String): Any =
      loadClass(NotSerializable::class.java.name).getConstructor(String::class.java).newInstance(id)

    /** Returns the mapper of this class loader's `BackStackEntryMapper.Companion.<getterName>`. */
    fun companionMapper(getterName: String): Mapper {
      val companion =
        loadClass(BackStackEntryMapper::class.java.name).getField("Companion").get(null)
      return Mapper(companion.javaClass.getMethod(getterName).invoke(companion))
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> =
      synchronized(this) {
        when {
          name == HIDDEN_CLASS && !hidesKotlinx -> {
            wasKotlinxMapperRequested = true
            throw ClassNotFoundException(name)
          }
          name.startsWith(KOTLINX_PACKAGE) && hidesKotlinx -> {
            wasKotlinxRequested = true
            throw ClassNotFoundException(name)
          }
          !name.startsWith(ISOLATED_PACKAGE) -> super.loadClass(name, resolve)
          else -> findLoadedClass(name) ?: define(name)
        }
      }

    private fun define(name: String): Class<*> {
      val bytes =
        parent.getResourceAsStream(name.replace('.', '/') + ".class")?.use { it.readBytes() }
          ?: throw ClassNotFoundException(name)
      return defineClass(name, bytes, 0, bytes.size)
    }

    /** A mapper loaded by this class loader, whose types differ from the test's. */
    class Mapper(val delegate: Any) {
      val type: Class<*> = delegate.javaClass

      fun map(backStackEntry: Any): Any? =
        type.getMethod("map", Any::class.java).invoke(delegate, backStackEntry)
    }

    private companion object {
      const val ISOLATED_PACKAGE = "io.sentry.compose.navigation3."
      const val KOTLINX_PACKAGE = "kotlinx.serialization."
      val HIDDEN_CLASS: String = KotlinxBackStackEntryMapper::class.java.name
    }
  }

  private companion object {
    /** Bounds every wait, so that a deadlock fails the test rather than hanging it. */
    const val TIMEOUT_SECONDS = 5L

    const val CREATION_FAILED = "Nav3 built-in mapper creation failed."

    /** Set by the module's Gradle build from `kotlinxSerializationFloor`. */
    const val KOTLINX_SERIALIZATION_FLOOR_PROPERTY = "nav3.kotlinxSerializationFloor"
  }
}
