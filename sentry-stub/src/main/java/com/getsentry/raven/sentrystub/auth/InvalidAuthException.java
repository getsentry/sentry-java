package com.getsentry.raven.sentrystub.auth;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

public class InvalidAuthException extends RuntimeException {
    private final Collection<String> detailedMessages = new LinkedList<>();

    public InvalidAuthException() {
    }

    public InvalidAuthException(String message) {
        super(message);
    }

    public InvalidAuthException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidAuthException(Throwable cause) {
        super(cause);
    }

    public Collection<String> getDetailedMessages() {
        return Collections.unmodifiableCollection(detailedMessages);
    }

    public void addDetailedMessage(String message) {
        detailedMessages.add(message);
    }

    public boolean isEmpty() {
        return detailedMessages.isEmpty();
    }
}
