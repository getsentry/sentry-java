package net.kencochrane.raven;

import mockit.Mock;
import mockit.MockClass;
import mockit.Mockit;
import org.junit.After;
import org.junit.Test;

import static net.kencochrane.raven.SentryDsn.DefaultLookUps;
import static net.kencochrane.raven.SentryDsn.LookUp;
import static org.junit.Assert.*;

/**
 * Test cases for {@link SentryDsn}.
 */
public class SentryDsnTest {

    @Test
    public void simple() {
        final String scheme = "http";
        final String publicKey = "7e4dff58960645adb2ade337e6d53425";
        final String secretKey = "81fe140206d7464e911b89cd93e2a5a4";
        final String host = "localhost";
        final int port = 9000;
        final String projectId = "2";
        String fullDsn = String.format("%s://%s:%s@%s:%d/%s", scheme, publicKey, secretKey, host, port, projectId);
        SentryDsn dsn = SentryDsn.build(fullDsn);
        // toString should map to the full DSN
        assertEquals(fullDsn, dsn.toString());
        assertEquals(scheme, dsn.scheme);
        assertEquals(publicKey, dsn.publicKey);
        assertEquals(secretKey, dsn.secretKey);
        assertEquals(host, dsn.host);
        assertEquals(port, dsn.port);
        assertEquals(projectId, dsn.projectId);
    }

    @Test
    public void schemeVariant() {
        final String variant = "naive";
        final String scheme = "https";
        final String publicKey = "7e4dff58960645adb2ade337e6d53425";
        final String secretKey = "81fe140206d7464e911b89cd93e2a5a4";
        final String host = "localhost";
        final int port = 9000;
        final String projectId = "2";
        String fullDsn = String.format("%s+%s://%s:%s@%s:%d/%s", variant, scheme, publicKey, secretKey, host, port, projectId);
        SentryDsn dsn = SentryDsn.build(fullDsn);
        // toString should map to the full DSN
        assertEquals(fullDsn, dsn.toString());
        // But toString(false) should report the clean URL
        String cleanDsn = String.format("%s://%s:%d", scheme, host, port);
        assertEquals(cleanDsn, dsn.toString(false));
        assertArrayEquals(new String[]{variant}, dsn.variants);
        assertEquals(scheme, dsn.scheme);
        assertEquals(publicKey, dsn.publicKey);
        assertEquals(secretKey, dsn.secretKey);
        assertEquals(host, dsn.host);
        assertEquals(port, dsn.port);
        assertEquals(projectId, dsn.projectId);
    }

    @Test(expected = SentryDsn.InvalidDsnException.class)
    public void emptyScheme() {
        SentryDsn.build("://public:secret@host/path/1");
    }

    @Test
    public void emptyScheme_optional() {
        assertNull(SentryDsn.buildOptional("://public:secret@host/path/1"));
    }

    @Test(expected = SentryDsn.InvalidDsnException.class)
    public void noScheme() {
        SentryDsn.build("public:secret@host/path/1");
    }

    @Test
    public void noScheme_optional() {
        assertNull(SentryDsn.buildOptional("public:secret@host/path/1"));
    }

    @Test
    public void noMalformedUrlErrorWithUdp() {
        SentryDsn dsn = SentryDsn.build("udp://public@host/path/goes/on/1");
        assertEquals("udp://public@host/path/goes/on/1", dsn.toString());
        assertEquals("udp://host/path/goes/on", dsn.toString(false));
        assertEquals(0, dsn.variants.length);
        assertEquals("udp", dsn.scheme);
        assertEquals("public", dsn.publicKey);
        org.junit.Assert.assertNull(dsn.secretKey);
        assertEquals("host", dsn.host);
        assertEquals("/path/goes/on", dsn.path);
        assertEquals("1", dsn.projectId);
    }

    @Test
    public void withOptions() {
        SentryDsn dsn = SentryDsn.build("async+http://public@host/path/goes/on/1?raven.go&raven.wait=true");
        assertEquals("http://host/path/goes/on", dsn.toString(false));
        assertTrue(dsn.isVariantIncluded("async"));
        assertEquals("http", dsn.scheme);
        assertEquals("host", dsn.host);
        assertEquals("/path/goes/on", dsn.path);
        assertEquals("1", dsn.projectId);
        assertEquals(2, dsn.options.size());
        assertTrue(dsn.options.containsKey("raven.go"));
        org.junit.Assert.assertNull(dsn.options.get("raven.go"));
        assertEquals("true", dsn.options.get("raven.wait"));
    }

    @Test
    public void applyOverrides() {
        final String envDsn = "http://a:b@host/path/1";
        final String systemPropertyDsn = "naive+https://k:l@domain/woah/there/15";
        final String suppliedDsn = "https://x:y@localhost/2";
        Mockit.setUpMock(MockSystem.class);
        MockSystem.dsn = envDsn;
        System.setProperty(Utils.SENTRY_DSN, systemPropertyDsn);
        SentryDsn dsn = SentryDsn.build();
        assertEquals(dsn.toString(true), envDsn);
        dsn = SentryDsn.build(suppliedDsn);
        assertEquals(dsn.toString(true), envDsn);
        dsn = SentryDsn.build(suppliedDsn, new LookUp[]{DefaultLookUps.SYSTEM_PROPERTY, DefaultLookUps.ENV}, null);
        assertEquals(dsn.toString(true), systemPropertyDsn);
        dsn = SentryDsn.build(suppliedDsn, null, null);
        assertEquals(dsn.toString(true), suppliedDsn);
    }

    @After
    public void tearDown() {
        System.setProperty(Utils.SENTRY_DSN, "");
    }

    @MockClass(realClass = System.class)
    public static class MockSystem {

        public static String dsn;

        @Mock
        public static String getenv(String s) {
            if (Utils.SENTRY_DSN.equals(s)) {
                return dsn;
            }
            return null;
        }
    }


}
