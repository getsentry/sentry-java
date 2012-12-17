package net.kencochrane.raven.logback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;

import net.kencochrane.raven.Utils;
import net.kencochrane.raven.spi.JSONProcessor;
import net.kencochrane.raven.spi.RavenMDC;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

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
        System.setProperty("logback.configurationFile", "");
        sentry.stop();
    }

    @Test
    public void noSentryDsn() {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).reset();
        System.setProperty(Utils.SENTRY_DSN, "");
        System.setProperty("logback.configurationFile", "sentryappender-no-dsn.logback.xml");
        LoggerFactory.getLogger(this.getClass()).debug("No Sentry DSN, no messages");
    }

    @Test
    public void invalidDsn() throws JoranException {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).reset();
        System.setProperty(Utils.SENTRY_DSN, "INVALID");
        configureLogback();
        LoggerFactory.getLogger(this.getClass()).debug("Invalid Sentry DSN, no messages");
    }

    @Test
    public void debugLevel() throws IOException, ParseException, JoranException {
        final String loggerName = "omg.logger";
        final long logLevel = (long) Level.DEBUG_INT / 1000;
        final String projectId = "1";
        final String message = "hi there!";

        configureLogback(projectId);
        LoggerFactory.getLogger(loggerName).debug(message);
        verifyMessage(loggerName, logLevel, "1", message);
    }

    @Test
    public void infoLevel() throws IOException, ParseException, JoranException {
        final String loggerName = "dude.wheres.my.ride";
        final long logLevel = (long) Level.INFO_INT / 1000;
        final String projectId = "2";
        final String message = "This message will self-destruct in 5...4...3...";

        configureLogback(projectId);
        LoggerFactory.getLogger(loggerName).info(message);
        verifyMessage(loggerName, logLevel, projectId, message);
    }

    @Test
    public void warnLevel() throws IOException, ParseException, JoranException {
        final String loggerName = "org.apache.commons.httpclient.andStuff";
        final long logLevel = (long) Level.WARN_INT / 1000;
        final String projectId = "20";
        final String message = "Warning! Warning! WARNING! Oh, come on!";

        configureLogback(projectId);
        LoggerFactory.getLogger(loggerName).warn(message);
        verifyMessage(loggerName, logLevel, projectId, message);
    }

    @Test
    public void errorLevel() throws IOException, ParseException, JoranException {
        final String loggerName = "org.apache.commons.httpclient.andStuff";
        final long logLevel = (long) Level.ERROR_INT / 1000;
        final String projectId = "5";
        final String message = "D'oh!";

        configureLogback(projectId);
        LoggerFactory.getLogger(loggerName).error(message);
        verifyMessage(loggerName, logLevel, projectId, message);
    }

    @Test
    public void errorLevel_withException() throws IOException, ParseException, JoranException {

        final String loggerName = "org.apache.commons.httpclient.andStuff";
        // When an exception is logged, the culprit should be the class+method
        // where the exception occurred
        final String culprit = getClass().getName() + ".errorLevel_withException";
        final long logLevel = (long) Level.ERROR_INT / 1000;
        final String projectId = "5";
        final String message = "D'oh!";

        configureLogback(projectId);
        NullPointerException npe = new NullPointerException("Damn you!");

        LoggerFactory.getLogger(loggerName).error(message, npe);

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

    protected void setSentryDSN(String projectId) {
        System.setProperty(Utils.SENTRY_DSN, String.format("udp://public:private@%s:%d/%s", sentry.host, sentry.port, projectId));
    }

    public void configureLogback(String projectId) throws JoranException {
        setSentryDSN(projectId);
        configureLogback();
    }

    public void configureLogback() throws JoranException {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator jc = new JoranConfigurator();
        jc.setContext(context);
        context.reset();
        jc.doConfigure(this.getClass().getClassLoader().getResource("sentryappender.logback.xml"));
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
            this("localhost", 9506);
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
            // this is done to ensure prepareDiagnosticContext is called exactly
            // once
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
