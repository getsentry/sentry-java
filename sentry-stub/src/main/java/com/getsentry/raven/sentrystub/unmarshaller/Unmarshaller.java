package com.getsentry.raven.sentrystub.unmarshaller;

import com.getsentry.raven.sentrystub.event.Event;

import java.io.InputStream;

public interface Unmarshaller {
    Event unmarshall(InputStream source);
}
