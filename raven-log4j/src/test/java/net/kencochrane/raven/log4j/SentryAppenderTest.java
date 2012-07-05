package net.kencochrane.raven.log4j;

import net.kencochrane.raven.Utils;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test cases for {@link SentryAppender}.
 */
public class SentryAppenderTest {

    private static DatagramSocket serverSocket;
    private static String host = "localhost";
    private static int port = 9505;

    @BeforeClass
    public static void beforeClass() throws SocketException {
        serverSocket = new DatagramSocket(new InetSocketAddress(host, port));
    }

    @AfterClass
    public static void afterClass() throws SocketException {
        System.setProperty(Utils.SENTRY_DSN, "");
        serverSocket.close();
    }

    @Test
    public void debugLevel() throws IOException, ParseException {
        final String loggerName = "omg.logger";
        final long logLevel = (long) Level.DEBUG_INT / 1000;
        final String projectId = "1";
        final String message = "hi there!";

        // Log
        System.setProperty(Utils.SENTRY_DSN, String.format("udp://public:private@%s:%d/%s", host, port, projectId));
        PropertyConfigurator.configure(getClass().getResource("/sentryappender.log4j.properties"));
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
        System.setProperty(Utils.SENTRY_DSN, String.format("udp://public:private@%s:%d/%s", host, port, projectId));
        PropertyConfigurator.configure(getClass().getResource("/sentryappender.log4j.properties"));
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
        System.setProperty(Utils.SENTRY_DSN, String.format("udp://public:private@%s:%d/%s", host, port, projectId));
        PropertyConfigurator.configure(getClass().getResource("/sentryappender.log4j.properties"));
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
        System.setProperty(Utils.SENTRY_DSN, String.format("udp://public:private@%s:%d/%s", host, port, projectId));
        PropertyConfigurator.configure(getClass().getResource("/sentryappender.log4j.properties"));
        Logger.getLogger(loggerName).error(message);

        // Verify
        verifyMessage(loggerName, logLevel, projectId, message);
    }

    @Test
    public void errorLevel_withException() throws IOException, ParseException {
        final String loggerName = "org.apache.commons.httpclient.andStuff";
        // When an exception is logged, the culprit should be the class+method where the exception occurred
        final String culprit = this.getClass().getName() + ".errorLevel_withException";
        final long logLevel = (long) Level.ERROR_INT / 1000;
        final String projectId = "5";
        final String message = "D'oh!";

        // Log
        System.setProperty(Utils.SENTRY_DSN, String.format("udp://public:private@%s:%d/%s", host, port, projectId));
        PropertyConfigurator.configure(getClass().getResource("/sentryappender.log4j.properties"));
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

    protected JSONObject verifyMessage(String culprit, long logLevel, String projectId, String message) throws IOException, ParseException {
        String payload = Utils.fromUtf8(fetchMessage());
        String[] payloadParts = StringUtils.split(payload, "\n\n");
        assertEquals(2, payloadParts.length);
        String raw = Utils.fromUtf8(Base64.decodeBase64(payloadParts[1]));
        JSONObject json = (JSONObject) new JSONParser().parse(raw);
        assertEquals(message, json.get("message"));
        assertEquals(culprit, json.get("culprit"));
        assertEquals(projectId, json.get("project"));
        assertEquals(logLevel, json.get("level"));
        return json;
    }

    protected static byte[] fetchMessage() throws IOException {
        DatagramPacket packet = new DatagramPacket(new byte[5048], 5048);
        serverSocket.receive(packet);
        return packet.getData();
    }

}
