package com.getsentry.raven.android;

import android.content.Context;
import com.getsentry.raven.*;
import com.getsentry.raven.connection.Connection;
import com.getsentry.raven.connection.HttpConnection;
import com.getsentry.raven.dsn.Dsn;

public class RavenFactory extends DefaultRavenFactory {
    private Context context;

    RavenFactory(Context ctx) {
        context = ctx;
    }

    @Override
    protected Connection createConnection(Dsn dsn) {
        String protocol = dsn.getProtocol();

        if (!(protocol.equalsIgnoreCase("http") || protocol.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only 'http' or 'https' connections are supported in"
                + " Raven Android, but received: " + protocol);
        }

        if ("false".equalsIgnoreCase(dsn.getOptions().get(DefaultRavenFactory.ASYNC_OPTION))) {
            throw new IllegalArgumentException("Raven Android cannot use synchronous connections, remove '"
                + DefaultRavenFactory.ASYNC_OPTION + "=false' from your DSN.");
        }

        HttpConnection httpConnection = (HttpConnection) createHttpConnection(dsn);
        Connection androidConnection = new com.getsentry.raven.android.Connection(context, httpConnection);
        return createAsyncConnection(dsn, androidConnection);
    }
}
