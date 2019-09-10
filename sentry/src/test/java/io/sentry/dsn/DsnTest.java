package io.sentry.dsn;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;

import java.net.URI;

import io.sentry.BaseTest;
import io.sentry.EmptyConfigurationProvider;
import io.sentry.config.Lookup;
import io.sentry.config.provider.ConfigurationProvider;
import io.sentry.util.Nullable;
import org.junit.Test;

public class DsnTest extends BaseTest {

    @Test(expected = InvalidDsnException.class)
    public void testEmptyDsnInvalid() throws Exception {
        new Dsn("");
    }

    @Test(expected = InvalidDsnException.class)
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
        assertThat(Dsn.dsnFrom(new Lookup(new EmptyConfigurationProvider(), new EmptyConfigurationProvider())),
                is(Dsn.DEFAULT_DSN));
    }

    @Test
    public void testEmptyStringAsDsnYieldsDefaultDsn() throws Exception {
        // given
        Lookup lookup = new Lookup(new ConfigurationProvider() {
            @Nullable
            @Override
            public String getProperty(String key) {
                return "dsn".equals(key) ? "" : null;
            }
        }, new EmptyConfigurationProvider());

        // then
        assertThat(Dsn.dsnFrom(lookup), is(Dsn.DEFAULT_DSN));
    }

    @Test(expected = InvalidDsnException.class)
    public void testMissingHostInvalid() throws Exception {
        new Dsn("http://publicKey:secretKey@/9");
    }

    @Test(expected = InvalidDsnException.class)
    public void testMissingPathInvalid() throws Exception {
        new Dsn("http://publicKey:secretKey@host");
    }

    @Test(expected = InvalidDsnException.class)
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

    @Test(expected = UnsupportedOperationException.class)
    public void testOptionsImmutable() throws Exception {
        Dsn dsn = new Dsn("http://publicKey:secretKey@host/9");

        dsn.getOptions().put("test", "test");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testProtocolSettingsImmutable() throws Exception {
        Dsn dsn = new Dsn("http://publicKey:secretKey@host/9");

        dsn.getProtocolSettings().add("test");
    }

    @Test
    public void testDsnEqualsMethodWithNoSecretKey() {
        final Dsn dsn1 = new Dsn("http://publicKey@host/9");
        final Dsn dsn2 = new Dsn("http://publicKey@host/9");
        assertThat(dsn1, equalTo(dsn2));
    }
}
