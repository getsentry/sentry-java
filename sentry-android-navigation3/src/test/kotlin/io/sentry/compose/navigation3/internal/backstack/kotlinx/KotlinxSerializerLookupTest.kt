package io.sentry.compose.navigation3.internal.backstack.kotlinx

import com.google.common.truth.Truth.assertThat
import java.lang.reflect.InvocationTargetException
import kotlin.test.assertFailsWith
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Test

class KotlinxSerializerLookupTest {

  @Serializable
  @SerialName("Route")
  sealed interface Route {
    @Serializable @SerialName("route.settings") data object Settings : Route
  }

  @Serializable
  @SerialName("Tab")
  enum class Tab {
    FEED,
  }

  @Serializable
  @SerialName("TabWithBodies")
  enum class TabWithBodies {
    FEED {
      override val icon = "feed"
    };

    abstract val icon: String
  }

  @Serializable(with = CustomSerializer::class) internal data class CustomSerialized(val id: String)

  internal object CustomSerializer : NamingOnlySerializer<CustomSerialized>() {
    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("custom.Name", PrimitiveKind.STRING)
  }

  @Serializable @SerialName("PrivateObject") private data object PrivateObject

  @Serializable @SerialName("PrivateClass") private data class PrivateClass(val id: String)

  data class NotSerializable(val id: String)

  /**
   * Looks like a private object to kotlinx.serialization, as it has a private static `INSTANCE`
   * field of its own type, but lacks the `serializer()` method the compiler plugin generates.
   */
  class InaccessibleInstanceWithoutSerializer private constructor() {
    private companion object {
      @Suppress("unused") private val INSTANCE = InaccessibleInstanceWithoutSerializer()
    }
  }

  /**
   * Looks like a private object to kotlinx.serialization, as for
   * [InaccessibleInstanceWithoutSerializer], but its `serializer()` method returns something other
   * than a serializer.
   */
  class InaccessibleInstanceWithNonSerializer private constructor() {
    @Suppress("unused", "FunctionOnlyReturningConstant") fun serializer(): Any = "not a serializer"

    private companion object {
      @Suppress("unused") private val INSTANCE = InaccessibleInstanceWithNonSerializer()
    }
  }

  enum class UnannotatedTab {
    FEED,
  }

  @Serializable data class Generic<T>(val value: T)

  // A class whose serializer's initializer threw can't be initialized again for the rest of the
  // JVM's life, so each test that triggers one gets its own pair, separate from other suites'.

  @Serializable(with = UninitializableSerializer::class) internal class Uninitializable

  internal object UninitializableSerializer : NamingOnlySerializer<Uninitializable>() {
    init {
      if (System.nanoTime() != 0L) error("serializer init failed")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("uninitializable", PrimitiveKind.STRING)
  }

  @Serializable(with = IncompatiblyUninitializableSerializer::class)
  internal class IncompatiblyUninitializable

  internal object IncompatiblyUninitializableSerializer :
    NamingOnlySerializer<IncompatiblyUninitializable>() {
    init {
      // As for FatallyUninitializableSerializer, the error isn't wrapped in an
      // ExceptionInInitializerError.
      if (System.nanoTime() != 0L) throw NoSuchMethodError("serializer init failed to link")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("incompatible", PrimitiveKind.STRING)
  }

  @Serializable(with = PrivateUninitializableSerializer::class)
  private object PrivateUninitializable

  private object PrivateUninitializableSerializer : NamingOnlySerializer<PrivateUninitializable>() {
    init {
      if (System.nanoTime() != 0L) error("private serializer init failed")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("private.uninitializable", PrimitiveKind.STRING)
  }

  @Serializable(with = FatallyUninitializableSerializer::class) private object PrivateFatal

  private object FatallyUninitializableSerializer : NamingOnlySerializer<PrivateFatal>() {
    init {
      // Unlike an exception, an error thrown by an initializer isn't wrapped in an
      // ExceptionInInitializerError.
      if (System.nanoTime() != 0L) throw OutOfMemoryError("serializer init failed fatally")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("fatal", PrimitiveKind.STRING)
  }

  @Serializable(with = PublicFatallyUninitializableSerializer::class)
  internal class FatallyUninitializable

  internal object PublicFatallyUninitializableSerializer :
    NamingOnlySerializer<FatallyUninitializable>() {
    init {
      if (System.nanoTime() != 0L) throw OutOfMemoryError("class serializer init failed fatally")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("fatal.class", PrimitiveKind.STRING)
  }

  // A serializer class, unlike an object, is constructed by the type's serializer() method, so an
  // exception its constructor throws reaches reflection without an ExceptionInInitializerError.

  @Serializable(with = FailingConstructionSerializer::class) internal class FailingConstruction

  internal class FailingConstructionSerializer : NamingOnlySerializer<FailingConstruction>() {
    init {
      if (System.nanoTime() != 0L) error("serializer construction failed")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("failingConstruction", PrimitiveKind.STRING)
  }

  @Serializable(with = InterruptedConstructionSerializer::class)
  internal class InterruptedConstruction

  internal class InterruptedConstructionSerializer :
    NamingOnlySerializer<InterruptedConstruction>() {
    init {
      if (System.nanoTime() != 0L) throw InterruptedException("serializer construction interrupted")
    }

    override val descriptor: SerialDescriptor =
      PrimitiveSerialDescriptor("interruptedConstruction", PrimitiveKind.STRING)
  }

  // @Serializable types

  @Test
  fun `returns the serializer of a @Serializable class`() {
    val serializer = reflectiveSerializerOrNull(Detail::class.java)

    assertThat(serializer?.descriptor?.serialName).isEqualTo("Detail")
  }

  @Test
  fun `returns the serializer of a @Serializable object`() {
    val serializer = reflectiveSerializerOrNull(Home::class.java)

    assertThat(serializer?.descriptor?.serialName).isEqualTo("Home")
  }

  @Test
  fun `returns the subtype's own serializer for a @Serializable sealed subtype`() {
    val serializer = reflectiveSerializerOrNull(Route.Settings::class.java)

    assertThat(serializer?.descriptor?.serialName).isEqualTo("route.settings")
  }

  @Test
  fun `returns the serializer of a @Serializable enum`() {
    val serializer = reflectiveSerializerOrNull(Tab::class.java)

    assertThat(serializer?.descriptor?.serialName).isEqualTo("Tab")
  }

  @Test
  fun `returns the custom serializer of a class that is @Serializable with a custom serializer`() {
    val serializer = reflectiveSerializerOrNull(CustomSerialized::class.java)

    assertThat(serializer?.descriptor?.serialName).isEqualTo("custom.Name")
  }

  @Test
  fun `returns the serializer of a private @Serializable object`() {
    val serializer = reflectiveSerializerOrNull(PrivateObject::class.java)

    assertThat(serializer?.descriptor?.serialName).isEqualTo("PrivateObject")
  }

  @Test
  fun `returns the serializer of a private @Serializable class`() {
    val serializer = reflectiveSerializerOrNull(PrivateClass::class.java)

    assertThat(serializer?.descriptor?.serialName).isEqualTo("PrivateClass")
  }

  // Types that aren't @Serializable but that kotlinx.serialization can serialize

  @Test
  fun `returns kotlinx serialization's serializers for built-in types`() {
    val types = listOf(String::class.java, Integer::class.java)

    val serialNames = types.map { reflectiveSerializerOrNull(it)?.descriptor?.serialName }

    assertThat(serialNames).containsExactly("kotlin.String", "kotlin.Int").inOrder()
  }

  @Test
  fun `returns kotlinx serialization's enum serializer for an enum that isn't @Serializable`() {
    val serializer = reflectiveSerializerOrNull(UnannotatedTab::class.java)

    assertThat(serializer?.descriptor?.serialName)
      .isEqualTo(UnannotatedTab::class.java.canonicalName)
  }

  // Types without a serializer to return

  @Test
  fun `returns null for a class that isn't @Serializable`() {
    val serializer = reflectiveSerializerOrNull(NotSerializable::class.java)

    assertThat(serializer).isNull()
  }

  @Test
  fun `returns null for the class of a @Serializable enum's constant that has a body`() {
    val serializer = reflectiveSerializerOrNull(TabWithBodies.FEED.javaClass)

    assertThat(serializer).isNull()
  }

  @Test
  fun `returns null for a generic @Serializable class`() {
    val serializer = reflectiveSerializerOrNull(Generic::class.java)

    assertThat(serializer).isNull()
  }

  @Test
  fun `returns null for a generic built-in collection type`() {
    val serializer = reflectiveSerializerOrNull(ArrayList::class.java)

    assertThat(serializer).isNull()
  }

  // Serializer initializers that throw

  @Test
  fun `throws a class's serializer initializer error wrapped in an InvocationTargetException`() {
    val thrown =
      assertFailsWith<InvocationTargetException> {
        reflectiveSerializerOrNull(Uninitializable::class.java)
      }

    assertThat(thrown).hasCauseThat().isInstanceOf(ExceptionInInitializerError::class.java)
    assertThat(thrown)
      .hasCauseThat()
      .hasCauseThat()
      .hasMessageThat()
      .isEqualTo("serializer init failed")
  }

  @Test
  fun `throws a class's serializer initializer NoSuchMethodError wrapped in an InvocationTargetException`() {
    val thrown =
      assertFailsWith<InvocationTargetException> {
        reflectiveSerializerOrNull(IncompatiblyUninitializable::class.java)
      }

    assertThat(thrown).hasCauseThat().isInstanceOf(NoSuchMethodError::class.java)
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("serializer init failed to link")
  }

  @Test
  fun `throws a private object's serializer initializer error wrapped in an InvocationTargetException`() {
    val thrown =
      assertFailsWith<InvocationTargetException> {
        reflectiveSerializerOrNull(PrivateUninitializable::class.java)
      }

    assertThat(thrown).hasCauseThat().isInstanceOf(ExceptionInInitializerError::class.java)
    assertThat(thrown)
      .hasCauseThat()
      .hasCauseThat()
      .hasMessageThat()
      .isEqualTo("private serializer init failed")
  }

  @Test
  fun `throws an exception from a class's serializer construction wrapped in an InvocationTargetException`() {
    val thrown =
      assertFailsWith<InvocationTargetException> {
        reflectiveSerializerOrNull(FailingConstruction::class.java)
      }

    assertThat(thrown).hasCauseThat().isInstanceOf(IllegalStateException::class.java)
    assertThat(thrown).hasCauseThat().hasMessageThat().isEqualTo("serializer construction failed")
  }

  @Test
  fun `restores the interrupt flag when a class's serializer construction is interrupted`() {
    try {
      val thrown =
        assertFailsWith<InvocationTargetException> {
          reflectiveSerializerOrNull(InterruptedConstruction::class.java)
        }

      assertThat(thrown).hasCauseThat().isInstanceOf(InterruptedException::class.java)
      assertThat(Thread.currentThread().isInterrupted).isTrue()
    } finally {
      Thread.interrupted()
    }
  }

  @Test
  fun `propagates a class's fatal serializer initializer error unwrapped`() {
    val thrown =
      assertFailsWith<OutOfMemoryError> {
        reflectiveSerializerOrNull(FatallyUninitializable::class.java)
      }

    assertThat(thrown).hasMessageThat().isEqualTo("class serializer init failed fatally")
  }

  @Test
  fun `propagates a private object's fatal serializer initializer error unwrapped`() {
    val thrown =
      assertFailsWith<OutOfMemoryError> {
        reflectiveSerializerOrNull(PrivateFatal::class.java)
      }

    assertThat(thrown).hasMessageThat().isEqualTo("serializer init failed fatally")
  }

  // Private object lookups that find no serializer

  @Test
  fun `throws the reflection error when a type with an inaccessible INSTANCE field has no serializer method`() {
    val thrown =
      assertFailsWith<NoSuchMethodException> {
        reflectiveSerializerOrNull(InaccessibleInstanceWithoutSerializer::class.java)
      }

    assertThat(thrown).hasMessageThat().contains("serializer")
  }

  @Test
  fun `returns null when a type with an inaccessible INSTANCE field has a serializer() that returns no KSerializer`() {
    val serializer = reflectiveSerializerOrNull(InaccessibleInstanceWithNonSerializer::class.java)

    assertThat(serializer).isNull()
  }
}
