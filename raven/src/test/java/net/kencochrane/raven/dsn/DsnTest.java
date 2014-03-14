package net.kencochrane.raven.dsn;

import mockit.Expectations;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.naming.Context;
import javax.naming.InitialContext;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class DsnTest {
    @Mocked
    private Context mockContext;

    @BeforeMethod
    public void setUp() throws Exception {
        InitialContextFactory.context = mockContext;
    }

    @Test(expectedExceptions = InvalidDsnException.class)
    public void testEmptyDsnInvalid() throws Exception {
        new Dsn("");
    }

    @Test
    public void testSimpleDsnValid() throws Exception {
        Dsn dsn = new Dsn("http://publicKey:secretKey@host/9");

        assertThat(dsn.getProtocol(), is("http"));
        assertThat(dsn.getPublicKey(), is("publicKey"));
        assertThat(dsn.getSecretKey(), is("secretKey"));
        assertThat(dsn.getHost(), is("host"));
        assertThat(dsn.getPath(), is("/"));
        assertThat(dsn.getProjectId(), is("9"));
    }

    @Test
    public void testDsnLookupWithNothingSet() throws Exception {
        assertThat(Dsn.dsnLookup(), is(nullValue()));
    }

    @Test
    public void testJndiLookupFailsWithException(@Mocked("jndiLookup") JndiLookup mockJndiLookup) throws Exception {
        new NonStrictExpectations(){{
            JndiLookup.jndiLookup();
            result = new ClassNotFoundException("Couldn't find the JNDI classes");
        }};

        assertThat(Dsn.dsnLookup(), is(nullValue()));
    }

    @Test
    public void testJndiLookupFailsWithError(@Mocked("jndiLookup") JndiLookup mockJndiLookup) throws Exception {
        new NonStrictExpectations(){{
            JndiLookup.jndiLookup();
            result = new NoClassDefFoundError("Couldn't find the JNDI classes");
        }};

        assertThat(Dsn.dsnLookup(), is(nullValue()));
    }

    @Test
    public void testDsnLookupWithJndi() throws Exception {
        final String dsn = UUID.randomUUID().toString();
        new Expectations() {{
            mockContext.lookup("java:comp/env/sentry/dsn");
            result = dsn;
        }};

        assertThat(Dsn.dsnLookup(), is(dsn));
    }

    @Test
    public void testDsnLookupWithSystemProperty() throws Exception {
        String dsn = UUID.randomUUID().toString();
        System.setProperty("SENTRY_DSN", dsn);

        assertThat(Dsn.dsnLookup(), is(dsn));

        System.clearProperty("SENTRY_DSN");
    }

    @Test
    public void testDsnLookupWithEnvironmentVariable() throws Exception {
        String dsn = UUID.randomUUID().toString();
        setEnv("SENTRY_DSN", dsn);

        assertThat(Dsn.dsnLookup(), is(dsn));

        removeEnv("SENTRY_DSN");
    }

    /**
     * Sets an environment variable during Unit-Test.
     * <p>
     * DO NOT USE THIS METHOD OUTSIDE OF THE UNIT TESTS!
     * </p>
     *
     * @param key   name of the environment variable.
     * @param value value for the variable.
     * @throws Exception if anything goes wrong.
     */
    @SuppressWarnings("unchecked")
    private void setEnv(String key, String value) throws Exception {
        Class[] classes = Collections.class.getDeclaredClasses();
        Map<String, String> env = System.getenv();
        for (Class cl : classes) {
            if (env.getClass().equals(cl)) {
                Field field = cl.getDeclaredField("m");
                field.setAccessible(true);
                Map<String, String> map = (Map<String, String>) field.get(env);
                map.put(key, value);
                break;
            }
        }
    }

    /**
     * Removes an environment variable during Unit-Test.
     * <p>
     * DO NOT USE THIS METHOD OUTSIDE OF THE UNIT TESTS!
     * </p>
     *
     * @param key name of the environment variable.
     * @throws Exception if anything goes wrong.
     */
    @SuppressWarnings("unchecked")
    private void removeEnv(String key) throws Exception {
        Class[] classes = Collections.class.getDeclaredClasses();
        Map<String, String> env = System.getenv();
        for (Class cl : classes) {
            if (env.getClass().equals(cl)) {
                Field field = cl.getDeclaredField("m");
                field.setAccessible(true);
                Map<String, String> map = (Map<String, String>) field.get(env);
                map.remove(key);
                break;
            }
        }
    }

    @Test(expectedExceptions = InvalidDsnException.class)
    public void testMissingSecretKeyInvalid() throws Exception {
        new Dsn("http://publicKey:@host/9");
    }

    @Test(expectedExceptions = InvalidDsnException.class)
    public void testMissingHostInvalid() throws Exception {
        new Dsn("http://publicKey:secretKey@/9");
    }

    @Test(expectedExceptions = InvalidDsnException.class)
    public void testMissingPathInvalid() throws Exception {
        new Dsn("http://publicKey:secretKey@host");
    }

    @Test(expectedExceptions = InvalidDsnException.class)
    public void testMissingProjectIdInvalid() throws Exception {
        new Dsn("http://publicKey:secretKey@host/");
    }

    @Test
    public void testAdvancedDsnValid() throws Exception {
        Dsn dsn = new Dsn("naive+udp://1234567890:0987654321@complete.host.name:1234" +
                "/composed/path/1029384756?option1&option2=valueOption2");

        assertThat(dsn.getProtocol(), is("udp"));
        assertThat(dsn.getProtocolSettings(), contains("naive"));
        assertThat(dsn.getPublicKey(), is("1234567890"));
        assertThat(dsn.getSecretKey(), is("0987654321"));
        assertThat(dsn.getHost(), is("complete.host.name"));
        assertThat(dsn.getPort(), is(1234));
        assertThat(dsn.getPath(), is("/composed/path/"));
        assertThat(dsn.getProjectId(), is("1029384756"));
        assertThat(dsn.getOptions(), hasKey("option1"));
        assertThat(dsn.getOptions(), hasKey("option2"));
        assertThat(dsn.getOptions().get("option2"), is("valueOption2"));
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testOptionsImmutable() throws Exception {
        Dsn dsn = new Dsn("http://publicKey:secretKey@host/9");

        dsn.getOptions().put("test", "test");
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testProtocolSettingsImmutable() throws Exception {
        Dsn dsn = new Dsn("http://publicKey:secretKey@host/9");

        dsn.getProtocolSettings().add("test");
    }
}
