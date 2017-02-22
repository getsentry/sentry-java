package com.getsentry.raven.unmarshaller;

import com.getsentry.raven.unmarshaller.event.UnmarshalledEvent;

import java.io.InputStream;

public interface Unmarshaller {
    UnmarshalledEvent unmarshal(InputStream source);
}