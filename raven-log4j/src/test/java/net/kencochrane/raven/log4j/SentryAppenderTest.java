package net.kencochrane.raven.log4j;

import net.kencochrane.raven.Utils;
import net.kencochrane.raven.spi.JSONProcessor;
import net.kencochrane.raven.spi.RavenMDC;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

import static org.junit.Assert.*;

/**
 * Test cases for {@link SentryAppender}.
 */
public class SentryAppenderTest {

    protected static SentryMock sentry;

    @BeforeClass
    public static void beforeClass() throws SocketException {
        sentry = new SentryMock();
    }

    @AfterClass
    public static void afterClass() throws SocketException {
        System.setProperty(Utils.SENTRY_DSN, "");
        sentry.stop();
    }

    @Test
    public void noSentryDsn() {
        PropertyConfigurator.configure(getClass().getResource("/sentryappender-no-dsn.log4j.properties"));
        Logger.getLogger(this.getClass()).debug("No Sentry DSN, no messages");
    }

    @Test
    public void invalidDsn() {
        configureLog4J();
        Logger.getLogger(this.getClass()).debug("No Sentry DSN, no messages");
    }

    @Test
    public void debugLevel() throws IOException, ParseException {
        final String loggerName = "omg.logger";
        final long logLevel = (long) Level.DEBUG_INT / 1000;
        final String projectId = "1";
        final String message = "hi there!";

        // Log
        setSentryDSN(projectId);
        configureLog4J();
        Logger.getLogger(loggerName).debug(message);

        // Verify log message
        verifyMessage(loggerName, logLevel, projectId, message);
    }

    @Test
    public void infoLevel() throws IOException, ParseException {
        final String loggerName = "dude.wheres.my.ride";
        final long logLevel = (long) Level.INFO_INT / 1000;
        final String projectId = "2";
        final String message = "This message will self-destruct in 5...4...3...";

        // Log
        setSentryDSN(projectId);
        configureLog4J();
        Logger.getLogger(loggerName).info(message);

        // And verify
        verifyMessage(loggerName, logLevel, projectId, message);
    }

    @Test
    public void warnLevel() throws IOException, ParseException {
        final String loggerName = "org.apache.commons.httpclient.andStuff";
        final long logLevel = (long) Level.WARN_INT / 1000;
        final String projectId = "20";
        final String message = "Warning! Warning! WARNING! Oh, come on!";

        // Log
        setSentryDSN(projectId);
        configureLog4J();
        Logger.getLogger(loggerName).warn(message);

        // And verify
        verifyMessage(loggerName, logLevel, projectId, message);
    }

    @Test
    public void errorLevel() throws IOException, ParseException {
        final String loggerName = "org.apache.commons.httpclient.andStuff";
        final long logLevel = (long) Level.ERROR_INT / 1000;
        final String projectId = "5";
        final String message = "D'oh!";

        // Log
        setSentryDSN(projectId);
        configureLog4J();
        Logger.getLogger(loggerName).error(message);

        // Verify
        verifyMessage(loggerName, logLevel, projectId, message);
    }

    @Test
    public void errorLevel_withException() throws IOException, ParseException {
        final String loggerName = "org.apache.commons.httpclient.andStuff";
        // When an exception is logged, the culprit should be the class+method where the exception occurred
        final String culprit = getClass().getName() + ".errorLevel_withException";
        final long logLevel = (long) Level.ERROR_INT / 1000;
        final String projectId = "5";
        final String message = "D'oh!";

        // Log
        setSentryDSN(projectId);
        configureLog4J();
        NullPointerException npe = new NullPointerException("Damn you!");
        Logger.getLogger(loggerName).error(message, npe);

        // Verify
        JSONObject json = verifyMessage(culprit, logLevel, projectId, message);
        JSONObject stacktrace = (JSONObject) json.get("sentry.interfaces.Stacktrace");
        assertNotNull(stacktrace);
        assertNotNull(stacktrace.get("frames"));
        JSONArray frames = (JSONArray) stacktrace.get("frames");
        assertTrue(frames.size() > 0);
        JSONObject exception = (JSONObject) json.get("sentry.interfaces.Exception");
        assertNotNull(exception);
        assertEquals(NullPointerException.class.getSimpleName(), exception.get("type"));
        assertEquals(npe.getMessage(), exception.get("value"));
        assertEquals(NullPointerException.class.getPackage().getName(), exception.get("module"));
    }

    @Test
    public void testJSONProcessors() throws IOException, ParseException {
        setSentryDSN("6");
        configureLog4J();
        Logger.getLogger("logger").info("test");
        JSONObject json = fetchJSONObject(sentry);
        assertEquals(1, ((Long) json.get("Test")).longValue());
    }

    @Test
    public void testClearMDC() throws IOException, ParseException {
        RavenMDC mdc = RavenMDC.getInstance();
        mdc.put("test", "test");
        assertNotNull(mdc.get("test"));
        setSentryDSN("6");
        configureLog4J();
        Logger.getLogger("logger").info("test");
        assertNull(RavenMDC.getInstance().get("test"));
    }

    protected void setSentryDSN(String projectId) {
        System.setProperty(Utils.SENTRY_DSN, String.format("udp://public:private@%s:%d/%s", sentry.host, sentry.port, projectId));
    }

    protected void configureLog4J() {
        PropertyConfigurator.configure(getClass().getResource("/sentryappender.log4j.properties"));
    }

    protected JSONObject verifyMessage(String culprit, long logLevel, String projectId, String message) throws IOException, ParseException {
        return verifyMessage(sentry, culprit, logLevel, projectId, message);
    }

    protected static JSONObject verifyMessage(SentryMock sentry, String culprit, long logLevel, String projectId, String message) throws IOException, ParseException {
        JSONObject json = fetchJSONObject(sentry);
        assertEquals(message, json.get("message"));
        assertEquals(culprit, json.get("culprit"));
        assertEquals(projectId, json.get("project"));
        assertEquals(logLevel, json.get("level"));
        return json;
    }

    protected static JSONObject fetchJSONObject(SentryMock sentry) throws IOException, ParseException {
        String payload = Utils.fromUtf8(sentry.fetchMessage());
        String[] payloadParts = StringUtils.split(payload, "\n\n");
        assertEquals(2, payloadParts.length);
        String raw = Utils.fromUtf8(Utils.decompress(Base64.decodeBase64(payloadParts[1])));
        return (JSONObject) new JSONParser().parse(raw);
    }

    public static class SentryMock {
        public final DatagramSocket serverSocket;
        public final String host;
        public final int port;

        public SentryMock() throws SocketException {
            this("localhost", 9505);
        }

        public SentryMock(String host, int port) throws SocketException {
            this.host = host;
            this.port = port;
            serverSocket = new DatagramSocket(new InetSocketAddress(host, port));
        }

        public void stop() {
            serverSocket.close();
        }

        public byte[] fetchMessage() throws IOException {
            DatagramPacket packet = new DatagramPacket(new byte[10000], 10000);
            serverSocket.receive(packet);
            return packet.getData();
        }

    }

    public static class MockJSONProcessor implements JSONProcessor {

        private Long value = 0L;

        @Override
        public void prepareDiagnosticContext() {
            // this is done to ensure prepareDiagnosticContext is called exactly once
            value++;
        }

        @Override
        public void clearDiagnosticContext() {
            RavenMDC.getInstance().remove("test");
        }

        @Override
        @SuppressWarnings("unchecked")
        public void process(JSONObject json, Throwable exception) {
            json.put("Test", value); // value should be 1
        }

    }

}
