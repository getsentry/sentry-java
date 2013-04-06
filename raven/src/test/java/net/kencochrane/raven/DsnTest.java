package net.kencochrane.raven;

import net.kencochrane.raven.exception.InvalidDsnException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DsnTest {
    @Test(expected = InvalidDsnException.class)
    public void testEmptyDsnInvalid() {
        new Dsn("");
    }

    @Test
    public void testSimpleDsnValid() {
        Dsn dsn = new Dsn("http://publicKey:secretKey@host/9");

        assertEquals("http", dsn.getProtocol());
        assertEquals("publicKey", dsn.getPublicKey());
        assertEquals("secretKey", dsn.getSecretKey());
        assertEquals("host", dsn.getHost());
        assertEquals("/", dsn.getPath());
        assertEquals("9", dsn.getProjectId());
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

        assertEquals("udp", dsn.getProtocol());
        assertTrue(dsn.getProtocolSettings().contains("naive"));
        assertEquals("1234567890", dsn.getPublicKey());
        assertEquals("0987654321", dsn.getSecretKey());
        assertEquals("complete.host.name", dsn.getHost());
        assertEquals(1234, dsn.getPort());
        assertEquals("/composed/path/", dsn.getPath());
        assertEquals("1029384756", dsn.getProjectId());
        assertTrue(dsn.getOptions().containsKey("option1"));
        assertEquals("valueOption2", dsn.getOptions().get("option2"));
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
