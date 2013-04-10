package net.kencochrane.raven;

import net.kencochrane.raven.exception.InvalidDsnException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.naming.Context;
import javax.naming.NamingException;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;

public class DsnTest {
    @Mock
    private Context context;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        System.setProperty("java.naming.factory.initial", InitialContextMockFactory.class.getCanonicalName());
        InitialContextMockFactory.context = context;
    }

    @Test(expected = InvalidDsnException.class)
    public void testEmptyDsnInvalid() {
        new Dsn("");
    }

    @Test
    public void testSimpleDsnValid() {
        Dsn dsn = new Dsn("http://publicKey:secretKey@host/9");

        assertThat(dsn.getProtocol(), is("http"));
        assertThat(dsn.getPublicKey(), is("publicKey"));
        assertThat(dsn.getSecretKey(), is("secretKey"));
        assertThat(dsn.getHost(), is("host"));
        assertThat(dsn.getPath(), is("/"));
        assertThat(dsn.getProjectId(), is("9"));
    }

    @Test
    public void testDsnLookupWithNothingSet() {
        assertThat(Dsn.dsnLookup(), is(nullValue()));
    }

    @Test
    public void testDsnLookupWithJndi() throws NamingException {
        String dsn = UUID.randomUUID().toString();
        when(context.lookup("java:comp/env/sentry/dsn")).thenReturn(dsn);

        assertThat(Dsn.dsnLookup(), is(dsn));
    }

    @Test(expected = InvalidDsnException.class)
    public void testMissingSecretKeyInvalid() {
        new Dsn("http://publicKey:@host/9");
    }

    @Test(expected = InvalidDsnException.class)
    public void testMissingHostInvalid() {
        new Dsn("http://publicKey:secretKey@/9");
    }

    @Test(expected = InvalidDsnException.class)
    public void testMissingPathInvalid() {
        new Dsn("http://publicKey:secretKey@host");
    }

    @Test(expected = InvalidDsnException.class)
    public void testMissingProjectIdInvalid() {
        new Dsn("http://publicKey:secretKey@host/");
    }

    @Test
    public void testAdvancedDsnValid() {
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
    public void testOptionsImmutable() {
        Dsn dsn = new Dsn("http://publicKey:secretKey@host/9");

        dsn.getOptions().put("test", "test");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testProtocolSettingsImmutable() {
        Dsn dsn = new Dsn("http://publicKey:secretKey@host/9");

        dsn.getProtocolSettings().add("test");
    }
}
