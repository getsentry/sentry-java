package io.sentry.dsn;

import io.sentry.BaseTest;
import io.sentry.config.JndiLookup;
import mockit.Expectations;
import mockit.Mocked;
import mockit.NonStrictExpectations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.naming.Context;
import java.net.URI;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class DsnTest extends BaseTest {
    @Mocked
    private Context mockContext = null;

    @BeforeMethod
    public void setUp() throws Exception {
        InitialContextFactory.context = mockContext;
    }

    @Test(expectedExceptions = InvalidDsnException.class)
    public void testEmptyDsnInvalid() throws Exception {
        new Dsn("");
    }

    @Test(expectedExceptions = InvalidDsnException.class)
    public void testDsnFromInvalidUri() throws Exception {
        new Dsn(URI.create(""));
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
    public void testSimpleDsnFromValidURI() throws Exception {
        Dsn dsn = new Dsn(URI.create("http://publicKey:secretKey@host/9"));

        assertThat(dsn.getProtocol(), is("http"));
        assertThat(dsn.getPublicKey(), is("publicKey"));
        assertThat(dsn.getSecretKey(), is("secretKey"));
        assertThat(dsn.getHost(), is("host"));
        assertThat(dsn.getPath(), is("/"));
        assertThat(dsn.getProjectId(), is("9"));
    }

    @Test
    public void testSimpleDsnNoSecretValid() throws Exception {
        Dsn dsn = new Dsn("http://publicKey@host/9");

        assertThat(dsn.getProtocol(), is("http"));
        assertThat(dsn.getPublicKey(), is("publicKey"));
        assertThat(dsn.getSecretKey(), isEmptyOrNullString());
        assertThat(dsn.getHost(), is("host"));
        assertThat(dsn.getPath(), is("/"));
        assertThat(dsn.getProjectId(), is("9"));
    }

    @Test
    public void testDsnLookupWithNothingSet() throws Exception {
        assertThat(Dsn.dsnLookup(), is(Dsn.DEFAULT_DSN));
    }

    @Test
    public void testJndiLookupFailsWithException(
            @SuppressWarnings("unused") @Mocked("jndiLookup") JndiLookup mockJndiLookup) throws Exception {
        new NonStrictExpectations() {{
            JndiLookup.jndiLookup("dsn");
            result = new ClassNotFoundException("Couldn't find the JNDI classes");
        }};

        assertThat(Dsn.dsnLookup(), is(Dsn.DEFAULT_DSN));
    }

    @Test
    public void testJndiLookupFailsWithError(
            @SuppressWarnings("unused") @Mocked("jndiLookup") JndiLookup mockJndiLookup) throws Exception {
        new NonStrictExpectations() {{
            JndiLookup.jndiLookup("dsn");
            result = new NoClassDefFoundError("Couldn't find the JNDI classes");
        }};

        assertThat(Dsn.dsnLookup(), is(Dsn.DEFAULT_DSN));
    }

    @Test
    public void testDsnLookupWithJndi() throws Exception {
        final String dsn = "6621980c-e27b-4dc9-9130-7fc5e9ea9750";
        new Expectations() {{
            mockContext.lookup("java:comp/env/sentry/dsn");
            result = dsn;
        }};

        assertThat(Dsn.dsnLookup(), is(dsn));
    }

    @Test
    public void testDsnLookupWithSystemProperty() throws Exception {
        String dsn = "aa9171a4-7e9b-4e3c-b3cc-fe537dc03527";
        System.setProperty("sentry.dsn", dsn);

        assertThat(Dsn.dsnLookup(), is(dsn));

        System.clearProperty("sentry.dsn");
    }

    @Test
    public void testDsnLookupWithEnvironmentVariable(@Mocked("getenv") final System system) throws Exception {
        final String dsn = "759ed060-dd4f-4478-8a1a-3f23e044787c";
        new NonStrictExpectations() {{
            System.getenv("SENTRY_DSN");
            result = dsn;
        }};

        assertThat(Dsn.dsnLookup(), is(dsn));
    }

    @Test
    public void testDsnLookupWithEmptyEnvironmentVariable(@Mocked("getenv") final System system) throws Exception {
        final String dsn = "";
        new NonStrictExpectations() {{
            System.getenv("SENTRY_DSN");
            result = dsn;
        }};

        assertThat(Dsn.dsnLookup(), is(Dsn.DEFAULT_DSN));
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
