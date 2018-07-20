package io.sentry.marshaller.json.connector;

import io.sentry.marshaller.json.factory.JsonFactoryImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.TestCase.assertSame;
import static org.easymock.EasyMock.createMock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JacksonTest {

    @Before
    public void setup() {
        JsonFactoryRuntimeClasspathLocator.JSON_FACTORY.set(null);
    }

    @After
    public void tearDown() throws Exception {
        JsonFactoryRuntimeClasspathLocator.JSON_FACTORY.set(null);
    }

    @Test
    public void testClassIsNotAvailable() {
        JsonFactoryRuntimeClasspathLocator locator = new JsonFactoryRuntimeClasspathLocator() {
            @Override
            protected boolean isAvailable(String fqcn) {
                return false;
            }
        };
        try {
            locator.getInstance();
        } catch (Exception ex) {
            assertEquals("Unable to discover any JsonFactory implementations on the classpath.", ex.getMessage());
        }
    }

    @Test
    public void testCompareAndSetFalse() {
        final JsonFactory deserializer = createMock(JsonFactory.class);

        JsonFactoryRuntimeClasspathLocator locator = new JsonFactoryRuntimeClasspathLocator() {
            @Override
            protected boolean compareAndSet(JsonFactory factory) {
                JsonFactoryRuntimeClasspathLocator.JSON_FACTORY.set(deserializer);
                return false;
            }
        };

        JsonFactory returned = locator.getInstance();
        assertSame(deserializer, returned);
    }

    @Test(expected = IllegalStateException.class)
    public void testLocateReturnsNull() {
        JsonFactoryRuntimeClasspathLocator locator = new JsonFactoryRuntimeClasspathLocator() {
            @Override
            protected JsonFactory locate() {
                return null;
            }
        };
        locator.getInstance();
    }

    @Test
    public void testJackson() {
        JsonFactory factory = new JsonFactoryRuntimeClasspathLocator().getInstance();
        assertTrue(factory instanceof JsonFactoryImpl);
    }
}
