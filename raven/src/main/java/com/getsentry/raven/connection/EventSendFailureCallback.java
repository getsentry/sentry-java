package com.getsentry.raven.connection;

import com.getsentry.raven.event.Event;

public interface EventSendFailureCallback {

    void onFailure(Event event, Exception exception);

}
