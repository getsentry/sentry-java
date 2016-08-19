package com.getsentry.raven.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
    private HttpConnection httpConnection;

    /**
     * Builds a Connection using the provided Android Context and underlying HttpConnection.
     *
     * @param ctx Android Connection
     * @param httpConnection HttpConnection
     */
    Connection(Context ctx, HttpConnection httpConnection) {
        context = ctx;
        this.httpConnection = httpConnection;
    }

    @Override
    public void send(Event event) {
        if (shouldAttemptToSend(context)) {
            Log.d(TAG, "attempting to send event to Sentry");
            httpConnection.send(event);
        } else {
            Log.d(TAG, "skipping event send because network is down");
            // TODO: in the future, store offline if we don't want to send here
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

    /**
     * Check whether the application has internet access at a point in time.
     *
     * @param ctx Android appliation ctx
     * @return true if the application has internet access
     */
    private boolean isConnected(Context ctx) {
        ConnectivityManager connectivityManager =
            (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    /**
     * Check whether Raven should attempt to send an event, or just immediately store it.
     *
     * @return true if Raven should attempt to send an event
     */
    private boolean shouldAttemptToSend(Context ctx) {
        if (!Util.checkPermission(ctx, android.Manifest.permission.ACCESS_NETWORK_STATE)) {
            // we can't check whether the connection is up, so the
            // best we can do is try
            return true;
        }

        return isConnected(ctx);
    }

}
