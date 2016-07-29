package com.getsentry.raven.android;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.getsentry.raven.connection.EventSendFailureCallback;
import com.getsentry.raven.connection.HttpConnection;
import com.getsentry.raven.event.Event;

import java.io.IOException;

public class Connection implements com.getsentry.raven.connection.Connection {
    private HttpConnection httpConnection;

    Connection(HttpConnection httpConnection) {
        this.httpConnection = httpConnection;
    }

    @Override
    public void send(Event event) {
        // TODO: use Android context to decide whether to send or not
        httpConnection.send(event);
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
