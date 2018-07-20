package io.sentry.marshaller.json.connector;

import java.util.concurrent.atomic.AtomicReference;

import io.sentry.marshaller.json.connector.classloading.*;

/**
 * Find a JsonFactory facade during runtime. This enables us to use Jackson, Gson or any other implementation.
 */
public class JsonFactoryRuntimeClasspathLocator implements InstanceLocator<JsonFactory> {

    /**
     * JsonFactory instance found in classpath if any.
     * Package-private for easier unit testing.
     */
    static final AtomicReference<JsonFactory> JSON_FACTORY = new AtomicReference<>();

    private static final String JSON_FACTORY_NAME = "io.sentry.marshaller.json.factory.JsonFactoryImpl";
//    private static final String JACKSON_FACTORY_NAME = "io.sentry.marshaller.json.factory.JsonFactoryImpl";
//    private static final String GSON_FACTORY_NAME = "io.sentry.marshaller.json.factory.JsonFactoryImpl";

    /**
     * Gets JsonFactory instance from classpath.
     * @return JsonFactory available at classpath
     * @throws IllegalStateException when classpath has no JsonFactory implementations.
     */
    @SuppressWarnings("unchecked")
    @Override
    public JsonFactory getInstance() {
        JsonFactory jsonFactory = JSON_FACTORY.get();
        if (jsonFactory == null) {
            jsonFactory = locate();
            assertState(jsonFactory != null, "locate() cannot return null.");
            if (!compareAndSet(jsonFactory)) {
                jsonFactory = JSON_FACTORY.get();
            }
        }
        assertState(jsonFactory != null, "factory cannot be null.");
        return jsonFactory;
    }

    /**
     * Locate class of JsonFactory available in classpath.
     * @return New JsonFactory instance.
     */
    @SuppressWarnings("WeakerAccess") //to allow testing override
    protected JsonFactory locate() {
        if (isAvailable(JSON_FACTORY_NAME)) {
            return Classes.newInstance(JSON_FACTORY_NAME);
//        if (isAvailable(JACKSON_FACTORY_NAME)) {
//            return Classes.newInstance(JACKSON_FACTORY_NAME);
//        } else if (isAvailable(GSON_FACTORY_NAME)) {
//            return Classes.newInstance(GSON_FACTORY_NAME);
        } else {
            throw new IllegalStateException("Unable to discover any JsonFactory implementations on the classpath.");
        }
    }

    /**
     * Update factory instance if not saved yet.
     * @param factory new factory created.
     * @return true if updated with new instance.
     */
    @SuppressWarnings("WeakerAccess") //to allow testing override
    protected boolean compareAndSet(JsonFactory factory) {
        return JSON_FACTORY.compareAndSet(null, factory);
    }

    /**
     * Checks if classname is available in classpath.
     * @see Classes#isAvailable(String)
     * @param fullyQualifiedName classname to search for.
     * @return true if found in classpath.
     */
    @SuppressWarnings({"WeakerAccess", "SameParameterValue"}) //to allow testing override
    protected boolean isAvailable(String fullyQualifiedName) {
        return Classes.isAvailable(fullyQualifiedName);
    }

    private static void assertState(boolean expression, String message) {
        if (!expression) {
            throw new IllegalStateException(message);
        }
    }
}
