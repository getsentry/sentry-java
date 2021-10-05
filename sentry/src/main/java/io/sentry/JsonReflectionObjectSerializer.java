package io.sentry;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Serialize any class to a map containing primitives.
 */
public final class JsonReflectionObjectSerializer {

  public Map<String, Object> serialize(Object object) throws Exception {
    Field[] fields = object.getClass().getDeclaredFields();

    Map<String, Object> map = new HashMap<>();

    for (Field field : fields) {
      if (Modifier.isTransient(field.getModifiers())) {
        continue;
      }
      String fieldName = field.getName();
      field.setAccessible(true);
      try {
        Class<?> fieldType = field.getType();
        Object fieldObject = field.get(object);
        map.put(fieldName, serialize(fieldType, fieldObject));
      } catch (IllegalAccessException e) {
        throw e;
      }
    }
    return map;
  }

  private @Nullable Object serialize(Class<?> fieldType, Object fieldObject) {
    if (fieldType == null || fieldObject == null) {
      return null;
    }
    if (fieldType.isPrimitive()) {
      return primitive(fieldObject);
    } else if (fieldObject instanceof String) {
      return primitive(fieldObject);
    } else if (fieldObject instanceof Collection) {
      return list((Collection<?>) fieldObject);
    } else if (fieldType.isArray()) {
      return list((Object[]) fieldObject);
    } else if (fieldObject instanceof Map) {
      return map((Map<?, ?>) fieldObject);
    }
    throw new IllegalArgumentException("TODO implement possibilities.");
  }

  // TODO Primitive arrays/collections?

  private List<Object> list(Object[] objectArray) {
    List<Object> list = new ArrayList<>();
    for (Object object : objectArray) {
      list.add(serialize(object.getClass(), object));
    }
    return list;
  }

  private List<Object> list(Collection<?> collection) {
    List<Object> list = new ArrayList<>();
    for (Object object : collection) {
      list.add(serialize(object.getClass(), object));
    }
    return list;
  }

  // TODO Primitives in map?
  // TODO Document toString key behaviour

  private Map<String, Object> map(Map<?, ?> map) {
    HashMap<String, Object> hashMap = new HashMap<>();
    for (Object key : map.keySet()) {
      Object object = map.get(key);
      if (object != null) {
        Class<?> clazz = object.getClass();
        hashMap.put(key.toString(), serialize(clazz, object));
      } else {
        hashMap.put(key.toString(), null);
      }
    }
    return hashMap;
  }
  private Object primitive(Object fieldObject) {
    if (fieldObject instanceof String) {
      return fieldObject;
    } else if (fieldObject instanceof Character) {
      return fieldObject.toString();
    } else if (fieldObject instanceof Byte) {
      return ((Byte) fieldObject).intValue();
    } else if (fieldObject instanceof Short) {
      return ((Short) fieldObject).intValue();
    } else if (fieldObject instanceof Integer) {
      return fieldObject;
    } else if (fieldObject instanceof Float) {
      return fieldObject;
    } else if (fieldObject instanceof Double) {
      return fieldObject;
    } else if (fieldObject instanceof Boolean) {
      return fieldObject;
    }
    throw new IllegalArgumentException("Not a primitive type.");
  }
}
