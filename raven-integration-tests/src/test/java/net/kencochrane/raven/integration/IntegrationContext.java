package net.kencochrane.raven.integration;

import net.kencochrane.raven.SentryApi;
import net.kencochrane.raven.SentryDsn;

import java.io.IOException;

/**
 * Context for integration tests.
 */
public class IntegrationContext {

    public static String host = "http://localhost";
    public static int port = 9500;
    public static int udpPort = 9501;
    public static SentryApi api;
    public static SentryDsn httpDsn;
    public static SentryDsn udpDsn;
    public static String projectSlug = "ravenjava";
    public static String projectId = "2";
    protected static boolean initialized = false;

    public static void init() throws IOException {
        api = new SentryApi(host + ":" + port);
        boolean loggedOn = api.login("test", "test");
        if (!loggedOn) {
            throw new RuntimeException("Could not log on");
        }
        String dsn = api.getDsn(projectSlug);
        httpDsn = SentryDsn.build(dsn, null, null);
        udpDsn = SentryDsn.build(dsn.replace("http://", "udp://").replace(":" + port, ":" + udpPort), null, null);
        initialized = true;
    }

}
