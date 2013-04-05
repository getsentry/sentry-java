package net.kencochrane.raven.event.interfaces;

import java.util.*;

/**
 * The Message interface for Sentry allows to add a message that will be formatted by sentry.
 */
public class MessageInterface implements SentryInterface {
    public static final String MESSAGE_INTERFACE = "sentry.interfaces.Message";
    private final String message;
    private final List<String> params;

    public MessageInterface(String message) {
        this(message, Collections.<String>emptyList());
    }

    public MessageInterface(String message, List<String> params) {
        this.message = message;
        this.params = Collections.unmodifiableList(new ArrayList<String>(params));
    }

    @Override
    public String getInterfaceName() {
        return MESSAGE_INTERFACE;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getParams() {
        return params;
    }
}
