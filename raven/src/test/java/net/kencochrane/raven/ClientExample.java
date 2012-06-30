package net.kencochrane.raven;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

/**
 * Simple client example.
 */
public class ClientExample {

    public static void main(String[] args) {
        initialize();
        String dsnString = null;
        if (args.length > 0) {
            dsnString = args[0];
        } else {
            dsnString = "async+http://7e4dff58960645adb2ade337e6d53425:81fe140206d7464e911b89cd93e2a5a4@localhost:9000/2";
        }
        SentryDsn dsn = SentryDsn.build(dsnString);
        System.out.println("Sending to Sentry instance at " + dsn.toString(false));
        Client client = new Client(dsn);
        System.out.println("Sending simple message");
        String eventId = client.captureMessage("Hi there");
        System.out.println("Simple message event id: " + eventId);
        try {
            throw new IllegalArgumentException("Oh no! You can't do *that*!");
        } catch (IllegalArgumentException e) {
            System.out.println("Logging exception");
            eventId = client.captureException(e);
            System.out.println("Exception event id: " + eventId);
        }
        client.stop();
        System.out.println("Stopped client");
    }

    protected static void initialize() {
        InputStream input = ClientExample.class.getResourceAsStream("/java.util.logging.properties");
        try {
            LogManager.getLogManager().readConfiguration(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                // Oh shut up
            }
        }
    }


}
