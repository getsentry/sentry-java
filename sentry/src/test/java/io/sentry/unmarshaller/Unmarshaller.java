package io.sentry.unmarshaller;

import io.sentry.unmarshaller.event.UnmarshalledEvent;

import java.io.InputStream;

public interface Unmarshaller {
    UnmarshalledEvent unmarshal(InputStream source);
}
