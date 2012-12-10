package net.kencochrane.raven.log4j;

import net.kencochrane.raven.Utils;
import net.kencochrane.raven.spi.RavenMDC;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.SocketException;

import static org.junit.Assert.*;

/**
 * Test cases for {@link AsyncSentryAppender}.
 */
public class AsyncSentryAppenderTest {

    protected static SentryAppenderTest.SentryMock sentry;

    @BeforeClass
    public static void beforeClass() throws SocketException {
        sentry = new SentryAppenderTest.SentryMock();
    }

    @AfterClass
    public static void afterClass() throws SocketException {
        System.setProperty(Utils.SENTRY_DSN, "");
        sentry.stop();
    }

    @Test
    public void noSentryDsn() {
        PropertyConfigurator.configure(getClass().getResource("/asyncsentryappender-no-dsn.log4j.properties"));
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
        JSONObject json = SentryAppenderTest.fetchJSONObject(sentry);
        assertEquals(1, ((Long)json.get("Test")).longValue());
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
        PropertyConfigurator.configure(getClass().getResource("/asyncsentryappender.log4j.properties"));
    }


    protected JSONObject verifyMessage(String loggerName, long logLevel, String projectId, String message) throws IOException, ParseException {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return SentryAppenderTest.verifyMessage(sentry, loggerName, logLevel, projectId, message);
    }

}
