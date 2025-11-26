package io.sentry.protocol

import io.sentry.ILogger
import io.sentry.JsonObjectReader
import io.sentry.JsonObjectWriter
import io.sentry.SentryItemType
import java.io.StringReader
import java.io.StringWriter
import kotlin.test.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock

class SentryItemTypeSerializationTest {
  class Fixture {
    val logger = mock<ILogger>()
  }

  private val fixture = Fixture()

  @Test
  fun serialize() {
    assertEquals(serialize(SentryItemType.Session), json("session"))
    assertEquals(serialize(SentryItemType.Event), json("event"))
    assertEquals(serialize(SentryItemType.UserFeedback), json("user_report"))
    assertEquals(serialize(SentryItemType.Attachment), json("attachment"))
    assertEquals(serialize(SentryItemType.Transaction), json("transaction"))
    assertEquals(serialize(SentryItemType.Profile), json("profile"))
    assertEquals(serialize(SentryItemType.ProfileChunk), json("profile_chunk"))
    assertEquals(serialize(SentryItemType.ClientReport), json("client_report"))
    assertEquals(serialize(SentryItemType.ReplayEvent), json("replay_event"))
    assertEquals(serialize(SentryItemType.ReplayRecording), json("replay_recording"))
    assertEquals(serialize(SentryItemType.ReplayVideo), json("replay_video"))
    assertEquals(serialize(SentryItemType.CheckIn), json("check_in"))
    assertEquals(serialize(SentryItemType.Feedback), json("feedback"))
    assertEquals(serialize(SentryItemType.Span), json("span"))
  }

  @Test
  fun deserialize() {
    assertEquals(deserialize(json("session")), SentryItemType.Session)
    assertEquals(deserialize(json("event")), SentryItemType.Event)
    assertEquals(deserialize(json("user_report")), SentryItemType.UserFeedback)
    assertEquals(deserialize(json("attachment")), SentryItemType.Attachment)
    assertEquals(deserialize(json("transaction")), SentryItemType.Transaction)
    assertEquals(deserialize(json("profile")), SentryItemType.Profile)
    assertEquals(deserialize(json("profile_chunk")), SentryItemType.ProfileChunk)
    assertEquals(deserialize(json("client_report")), SentryItemType.ClientReport)
    assertEquals(deserialize(json("replay_event")), SentryItemType.ReplayEvent)
    assertEquals(deserialize(json("replay_recording")), SentryItemType.ReplayRecording)
    assertEquals(deserialize(json("replay_video")), SentryItemType.ReplayVideo)
    assertEquals(deserialize(json("check_in")), SentryItemType.CheckIn)
    assertEquals(deserialize(json("feedback")), SentryItemType.Feedback)
    assertEquals(deserialize(json("span")), SentryItemType.Span)
  }

  private fun json(type: String): String = "{\"type\":\"${type}\"}"

  private fun serialize(src: SentryItemType): String {
    val wrt = StringWriter()
    val jsonWrt = JsonObjectWriter(wrt, 100)
    jsonWrt.beginObject()
    jsonWrt.name("type")
    src.serialize(jsonWrt, fixture.logger)
    jsonWrt.endObject()
    return wrt.toString()
  }

  private fun deserialize(json: String): SentryItemType {
    val reader = JsonObjectReader(StringReader(json))
    reader.beginObject()
    reader.nextName()
    return SentryItemType.Deserializer().deserialize(reader, fixture.logger)
  }
}



## 1. **Sentinel Object Pattern**

```dart
// Create a private sentinel marker
const _undefined = Object();

static Span startSpan(String name, {
    Map<String, SentryAttribute>? attributes,
    Object? parentSpan = _undefined, // Uses Object? to accept Span?, null, or undefined
    bool active = true,
}) {
  final Span? actualParentSpan;
  if (identical(parentSpan, _undefined)) {
    // Not provided - use default behavior (e.g., get from current scope)
    actualParentSpan = _getCurrentSpan();
  } else {
    // Explicitly provided (could be null or a Span)
    actualParentSpan = parentSpan as Span?;
  }
  // ...
}
```

## 2. **Wrapper Class (Optional/Maybe)**

```dart
class Optional<T> {
  final T? value;
  final bool isSet;
  
  const Optional.absent() : value = null, isSet = false;
  const Optional.of(this.value) : isSet = true;
}

static Span startSpan(String name, {
    Optional<Span> parentSpan = const Optional.absent(),
}) {
  if (parentSpan.isSet) {
    // Explicitly provided (value could be null or a Span)
  } else {
    // Not provided
  }
}
```

## 3. **Sealed Classes (Dart 3+)** — Most Type-Safe

```dart
sealed class ParentSpanOption {}
class UseCurrentSpan extends ParentSpanOption {} // undefined - use default
class ExplicitSpan extends ParentSpanOption {
  final Span? span; // can be null or a span
  ExplicitSpan(this.span);
}

static Span startSpan(String name, {
    ParentSpanOption parentSpan = const UseCurrentSpan(),
}) {
  switch (parentSpan) {
    case UseCurrentSpan():
      // Not provided - use default behavior
    case ExplicitSpan(span: final span):
      // Explicitly provided (span or null)
  }
}
```

## 4. **Separate Boolean Flag**

```dart
static Span startSpan(String name, {
    Span? parentSpan,
    bool parentSpanProvided = false, // true means parentSpan was explicitly set
    bool active = true,
}) {
  if (parentSpanProvided) {
    // Use parentSpan (even if null)
  } else {
    // Not provided - use default
  }
}
```

## 5. **Record Type (Dart 3+)**

```dart
static Span startSpan(String name, {
    (bool isSet, Span? value)? parentSpan, // null = undefined, (true, x) = explicit
}) {
  if (parentSpan == null) {
    // undefined
  } else {
    final (_, span) = parentSpan;
    // explicitly set to `span` (which could be null)
  }
}
```
