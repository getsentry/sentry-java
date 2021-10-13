package io.sentry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/*
 * Transform any class to primitives, collections and maps.
 */
@ApiStatus.Internal
public final class JsonReflectionObjectSerializer {

  public @Nullable Object serialize(@Nullable Object object) throws Exception {
    if (object == null) {
      return null;
    }
    if (object instanceof Character) {
      return object.toString();
    } else if (object instanceof Byte) {
      return ((Byte) object).intValue();
    } else if (object instanceof Short) {
      return ((Short) object).intValue();
    } else if (object instanceof Integer) {
      return object;
    } else if (object instanceof Float) {
      return object;
    } else if (object instanceof Double) {
      return object;
    } else if (object instanceof Boolean) {
      return object;
    } else if (object.getClass().isArray()) {
      return list((Object[]) object);
    } else if (object instanceof String) {
      return object;
    } else if (object instanceof Collection) {
      return list((Collection<?>) object);
    } else if (object instanceof Map) {
      return map((Map<?, ?>) object);
    } else {
      return serializeObject(object);
    }
  }

  public @NotNull Map<String, Object> serializeObject(@NotNull Object object) throws Exception {
    Field[] fields = object.getClass().getDeclaredFields();

    Map<String, Object> map = new TreeMap<>();

    for (Field field : fields) {
      if (Modifier.isTransient(field.getModifiers())) {
        continue;
      }
      String fieldName = field.getName();
      field.setAccessible(true);

      Object fieldObject = field.get(object);
      map.put(fieldName, serialize(fieldObject));

      field.setAccessible(false);
    }
    return map;
  }

  private @NotNull List<Object> list(@NotNull Object[] objectArray) throws Exception {
    List<Object> list = new ArrayList<>();
    for (Object object : objectArray) {
      list.add(serialize(object));
    }
    return list;
  }

  private @NotNull List<Object> list(@NotNull Collection<?> collection) throws Exception {
    List<Object> list = new ArrayList<>();
    for (Object object : collection) {
      list.add(serialize(object));
    }
    return list;
  }

  // Key names taken from toString
  private @NotNull Map<String, Object> map(@NotNull Map<?, ?> map) throws Exception {
    Map<String, Object> hashMap = new TreeMap<>();
    for (Object key : map.keySet()) {
      Object object = map.get(key);
      if (object != null) {
        hashMap.put(key.toString(), serialize(object));
      } else {
        hashMap.put(key.toString(), null);
      }
    }
    return hashMap;
  }
}
