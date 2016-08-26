package com.getsentry.raven.android;

import android.content.Context;
import android.util.Log;
import com.getsentry.raven.connection.EventSendFailureCallback;
import com.getsentry.raven.connection.HttpConnection;
import com.getsentry.raven.event.Event;

import java.io.IOException;

/**
 * Connection implementation that handles Android specific logic such as
 * checking for internet connectivity.
 */
public class Connection implements com.getsentry.raven.connection.Connection {

    /**
     * Logger tag.
     */
    private static final String TAG = Raven.class.getName();

    private Context context;
    private EventCache eventCache;
    private HttpConnection httpConnection;

    /**
     * Builds a Connection using the provided Android Context and underlying HttpConnection.
     *
     * @param ctx Android Connection
     * @param eventCache EventCache used to store Events when offline
     * @param httpConnection HttpConnection
     */
    Connection(Context ctx, EventCache eventCache, HttpConnection httpConnection) {
        context = ctx;
        this.eventCache = eventCache;
        this.httpConnection = httpConnection;
    }

    @Override
    public void send(Event event) {
        if (Util.shouldAttemptToSend(context)) {
            Log.d(TAG, "Attempting to send event to Sentry.");
            httpConnection.send(event);
        } else {
            Log.d(TAG, "Skipping event send because network is down.");
            eventCache.storeEvent(event);
        }
    }

    @Override
    public void addEventSendFailureCallback(EventSendFailureCallback eventSendFailureCallback) {
        httpConnection.addEventSendFailureCallback(eventSendFailureCallback);
    }

    @Override
    public void close() throws IOException {
        httpConnection.close();
    }


}
