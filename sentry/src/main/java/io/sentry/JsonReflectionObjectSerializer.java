package io.sentry;

import static io.sentry.SentryLevel.INFO;
import static io.sentry.util.JsonSerializationUtils.atomicIntegerArrayToList;
import static io.sentry.util.JsonSerializationUtils.calendarToMap;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
 * Transform any class to primitives, collections and maps.
 */
@ApiStatus.Internal
public final class JsonReflectionObjectSerializer {

  private final Set<Object> visiting = new HashSet<>();
  private final int maxDepth;

  JsonReflectionObjectSerializer(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  public @Nullable Object serialize(@Nullable Object object, @NotNull ILogger logger)
      throws Exception {
    if (object == null) {
      return null;
    }
    if (object instanceof Character) {
      return object.toString();
    } else if (object instanceof Number) {
      return object;
    } else if (object instanceof Boolean) {
      return object;
    } else if (object instanceof String) {
      return object;
    } else if (object instanceof Locale) {
      return object.toString();
    } else if (object instanceof AtomicIntegerArray) {
      return atomicIntegerArrayToList((AtomicIntegerArray) object);
    } else if (object instanceof AtomicBoolean) {
      return ((AtomicBoolean) object).get();
    } else if (object instanceof URI) {
      return object.toString();
    } else if (object instanceof InetAddress) {
      return object.toString();
    } else if (object instanceof UUID) {
      return object.toString();
    } else if (object instanceof Currency) {
      return object.toString();
    } else if (object instanceof Calendar) {
      return calendarToMap((Calendar) object);
    } else if (object.getClass().isEnum()) {
      return object.toString();
    } else {
      if (visiting.contains(object)) {
        if (logger.isEnabled(SentryLevel.INFO)) {
          logger.log(SentryLevel.INFO, "Cyclic reference detected. Calling toString() on object.");
        }
        return object.toString();
      }
      visiting.add(object);

      if (visiting.size() > maxDepth) {
        visiting.remove(object);
        if (logger.isEnabled(SentryLevel.INFO)) {
          logger.log(SentryLevel.INFO, "Max depth exceeded. Calling toString() on object.");
        }
        return object.toString();
      }

      Object serializedObject = null;
      try {
        if (object.getClass().isArray()) {
          serializedObject = list((Object[]) object, logger);
        } else if (object instanceof Collection) {
          serializedObject = list((Collection<?>) object, logger);
        } else if (object instanceof Map) {
          serializedObject = map((Map<?, ?>) object, logger);
        } else {
          @NotNull Map<String, Object> objectAsMap = serializeObject(object, logger);
          if (objectAsMap.isEmpty()) {
            serializedObject = object.toString();
          } else {
            serializedObject = objectAsMap;
          }
        }
      } catch (Exception exception) {
        if (logger.isEnabled(INFO)) {
          logger.log(
              SentryLevel.INFO, "Not serializing object due to throwing sub-path.", exception);
        }
      } finally {
        visiting.remove(object);
      }
      return serializedObject;
    }
  }

  public @NotNull Map<String, Object> serializeObject(
      @NotNull Object object, @NotNull ILogger logger) throws Exception {
    Field[] fields = object.getClass().getDeclaredFields();

    Map<String, Object> map = new HashMap<>();

    for (Field field : fields) {
      if (Modifier.isTransient(field.getModifiers())) {
        continue;
      }
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      String fieldName = field.getName();
      try {
        field.setAccessible(true);
        Object fieldObject = field.get(object);
        map.put(fieldName, serialize(fieldObject, logger));
        field.setAccessible(false);
      } catch (Exception exception) {
        if (logger.isEnabled(SentryLevel.INFO)) {
          logger.log(SentryLevel.INFO, "Cannot access field " + fieldName + ".");
        }
      }
    }

    return map;
  }

  // Helper

  private @NotNull List<Object> list(@NotNull Object[] objectArray, @NotNull ILogger logger)
      throws Exception {
    List<Object> list = new ArrayList<>();
    for (Object object : objectArray) {
      list.add(serialize(object, logger));
    }
    return list;
  }

  private @NotNull List<Object> list(@NotNull Collection<?> collection, @NotNull ILogger logger)
      throws Exception {
    List<Object> list = new ArrayList<>();
    for (Object object : collection) {
      list.add(serialize(object, logger));
    }
    return list;
  }

  // Key names taken from toString
  private @NotNull Map<String, Object> map(@NotNull Map<?, ?> map, @NotNull ILogger logger)
      throws Exception {
    Map<String, Object> hashMap = new HashMap<>();
    for (Object key : map.keySet()) {
      Object object = map.get(key);
      if (object != null) {
        hashMap.put(key.toString(), serialize(object, logger));
      } else {
        hashMap.put(key.toString(), null);
      }
    }
    return hashMap;
  }
}
