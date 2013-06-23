package net.kencochrane.raven.event.interfaces;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Message interface for Sentry allows to add a message that will be formatted by sentry.
 */
public class MessageInterface implements SentryInterface {
    /**
     * Name of the message interface in Sentry.
     */
    public static final String MESSAGE_INTERFACE = "sentry.interfaces.Message";
    private final String message;
    private final List<String> params;

    /**
     * Creates a non parametrised message.
     * <p>
     * While it's technically possible to create a non parametrised message with {@code MessageInterface}, it's
     * recommended to use {@link net.kencochrane.raven.event.EventBuilder#setMessage(String)} instead.
     * </p>
     *
     * @param message message to add to the event.
     * @deprecated Use {@link net.kencochrane.raven.event.EventBuilder#setMessage(String)} instead.
     */
    @Deprecated
    public MessageInterface(String message) {
        this(message, Collections.<String>emptyList());
    }

    public MessageInterface(String message, String... params) {
        this(message, Arrays.asList(params));
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
