package com.getsentry.raven.android;

import com.getsentry.raven.DefaultRavenFactory;
import com.getsentry.raven.connection.Connection;
import com.getsentry.raven.connection.EventSendFailureCallback;
import com.getsentry.raven.dsn.Dsn;
import com.getsentry.raven.event.Event;

public class RavenFactory extends DefaultRavenFactory {

    @Override
    protected Connection createConnection(Dsn dsn) {
        Connection conn = super.createConnection(dsn);
        conn.addEventSendFailureCallback(new EventSendFailureCallback() {
            @Override
            public void onFailure(Event event, Exception exception) {
                // TODO: store to local storage
            }
        });
        return conn;
    }
}
