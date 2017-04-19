package io.sentry.connection;

import io.sentry.DefaultSentryFactory;
import io.sentry.Sentry;
import io.sentry.dsn.Dsn;
import io.sentry.event.Event;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class EventSendFailureCallbackTest {

    @Test
    public void testSimpleCallback() {
        final AtomicBoolean flag = new AtomicBoolean(false);

        DefaultSentryFactory factory = new DefaultSentryFactory() {
            @Override
            protected Connection createConnection(Dsn dsn) {
                Connection connection = super.createConnection(dsn);

                connection.addEventSendFailureCallback(new EventSendFailureCallback() {
                    @Override
                    public void onFailure(Event event, Exception exception) {
                        flag.set(true);
                    }
                });

                return connection;
            }
        };

        String dsn = "https://foo:bar@localhost:1234/1?async=false";
        Sentry sentry = factory.createSentryInstance(new Dsn(dsn));
        sentry.sendMessage("Message that will fail because DSN points to garbage.");

        assertThat(flag.get(), is(true));
    }

    @Test
    public void testExceptionInsideCallback() {
        DefaultSentryFactory factory = new DefaultSentryFactory() {
            @Override
            protected Connection createConnection(Dsn dsn) {
                Connection connection = super.createConnection(dsn);

                connection.addEventSendFailureCallback(new EventSendFailureCallback() {
                    @Override
                    public void onFailure(Event event, Exception exception) {
                        throw new RuntimeException("Error inside of EventSendFailureCallback");
                    }
                });

                return connection;
            }
        };

        String dsn = "https://foo:bar@localhost:1234/1?async=false";
        Sentry sentry = factory.createSentryInstance(new Dsn(dsn));
        sentry.sendMessage("Message that will fail because DSN points to garbage.");
    }

}
