package io.sentry.core;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.Excluder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class UnknownPropertiesTypeAdapterFactory implements TypeAdapterFactory {

  private static final TypeAdapterFactory instance = new UnknownPropertiesTypeAdapterFactory();

  private UnknownPropertiesTypeAdapterFactory() {}

  static TypeAdapterFactory get() {
    return instance;
  }

  @Override
  public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> typeToken) {
    // Check if we can deal with the given type
    if (!IUnknownPropertiesConsumer.class.isAssignableFrom(typeToken.getRawType())) {
      return null;
    }
    // If we can, we should get the backing class to fetch its fields from
    @SuppressWarnings("unchecked")
    final Class<IUnknownPropertiesConsumer> rawType =
        (Class<IUnknownPropertiesConsumer>) typeToken.getRawType();
    @SuppressWarnings("unchecked")
    final TypeAdapter<IUnknownPropertiesConsumer> delegateTypeAdapter =
        (TypeAdapter<IUnknownPropertiesConsumer>) gson.getDelegateAdapter(this, typeToken);
    // Excluder is necessary to check if the field can be processed
    // Basically it's not required, but it makes the check more complete
    final Excluder excluder = gson.excluder();
    // This is crucial to map fields and JSON object properties since Gson supports name remapping
    final FieldNamingStrategy fieldNamingStrategy = gson.fieldNamingStrategy();
    final TypeAdapter<IUnknownPropertiesConsumer> unknownPropertiesTypeAdapter =
        UnknownPropertiesTypeAdapter.create(
            rawType, delegateTypeAdapter, excluder, fieldNamingStrategy);
    @SuppressWarnings("unchecked")
    final TypeAdapter<T> castTypeAdapter = (TypeAdapter<T>) unknownPropertiesTypeAdapter;
    return castTypeAdapter;
  }

  private static final class UnknownPropertiesTypeAdapter<T extends IUnknownPropertiesConsumer>
      extends TypeAdapter<T> {

    private final TypeAdapter<T> typeAdapter;
    private final Collection<String> propertyNames;

    private UnknownPropertiesTypeAdapter(
        final TypeAdapter<T> typeAdapter, final Collection<String> propertyNames) {
      this.typeAdapter = typeAdapter;
      this.propertyNames = propertyNames;
    }

    private static <T extends IUnknownPropertiesConsumer> TypeAdapter<T> create(
        final Class<? super T> clazz,
        final TypeAdapter<T> typeAdapter,
        final Excluder excluder,
        final FieldNamingStrategy fieldNamingStrategy) {
      final Collection<String> propertyNames =
          getPropertyNames(clazz, excluder, fieldNamingStrategy);
      return new UnknownPropertiesTypeAdapter<>(typeAdapter, propertyNames);
    }

    private static Collection<String> getPropertyNames(
        final Class<?> clazz,
        final Excluder excluder,
        final FieldNamingStrategy fieldNamingStrategy) {
      final Collection<String> propertyNames = new ArrayList<>();
      // Class fields are declared per class so we have to traverse the whole hierarchy
      for (Class<?> i = clazz;
          i.getSuperclass() != null && i != Object.class;
          i = i.getSuperclass()) {
        for (final Field declaredField : i.getDeclaredFields()) {
          // If the class field is not excluded
          if (!excluder.excludeField(declaredField, false)) {
            // We can translate the field name to its property name counter-part
            final String propertyName = fieldNamingStrategy.translateName(declaredField);
            propertyNames.add(propertyName);
          }
        }
      }
      return propertyNames;
    }

    @Override
    public void write(final JsonWriter out, final T value) throws IOException {
      typeAdapter.write(out, value);
    }

    @Override
    public T read(final JsonReader in) {
      // In its simplest solution, we can just collect a JSON tree because its much easier to
      // process
      JsonParser parser = new JsonParser();
      JsonElement jsonElement = parser.parse(in);

      if (jsonElement == null || jsonElement.isJsonNull()) {
        return null;
      }

      final JsonObject jsonObjectToParse = jsonElement.getAsJsonObject();
      Map<String, Object> unknownProperties = new HashMap<>();
      for (final Map.Entry<String, JsonElement> e : jsonObjectToParse.entrySet()) {
        final String propertyName = e.getKey();
        // Not in the object fields?
        if (!propertyNames.contains(propertyName)) {
          // Then we assume the property is unknown
          unknownProperties.put(propertyName, e.getValue());
        }
      }
      // First convert the above JSON tree to an object
      final T object = typeAdapter.fromJsonTree(jsonObjectToParse);
      if (!unknownProperties.isEmpty()) {
        // And do the post-processing
        object.acceptUnknownProperties(unknownProperties);
      }
      return object;
    }
  }
}
