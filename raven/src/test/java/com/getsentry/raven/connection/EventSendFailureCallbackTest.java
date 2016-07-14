package com.getsentry.raven.connection;

import com.getsentry.raven.DefaultRavenFactory;
import com.getsentry.raven.Raven;
import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.event.Event;
import org.testng.annotations.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class EventSendFailureCallbackTest {

    @Test
    public void testSimpleCallback() {
        final AtomicBoolean flag = new AtomicBoolean(false);

        DefaultRavenFactory factory = new DefaultRavenFactory() {
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

        String dsn = "https://foo:bar@localhost:1234/1?raven.async=false";
        Raven raven = factory.createRavenInstance(new Dsn(dsn));
        raven.sendMessage("Message that will fail because DSN points to garbage.");

        assertThat(flag.get(), is(true));
    }

    @Test
    public void testExceptionInsideCallback() {
        DefaultRavenFactory factory = new DefaultRavenFactory() {
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

        String dsn = "https://foo:bar@localhost:1234/1?raven.async=false";
        Raven raven = factory.createRavenInstance(new Dsn(dsn));
        raven.sendMessage("Message that will fail because DSN points to garbage.");
    }

}
