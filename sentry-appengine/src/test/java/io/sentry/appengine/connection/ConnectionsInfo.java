package io.sentry.appengine.connection;

import io.sentry.util.Nullable;

/**
 * This is a helper class to extract information about the {@link AppEngineAsyncConnection} for the test purposes.
 *
 * This is used by the tests living outside of this package to be able to access package-private information.
 */
public class ConnectionsInfo {

    @Nullable
    public static AppEngineAsyncConnection getExistingConnectionById(String id) {
        return AppEngineAsyncConnection.APP_ENGINE_ASYNC_CONNECTIONS.get(id);
    }
}
