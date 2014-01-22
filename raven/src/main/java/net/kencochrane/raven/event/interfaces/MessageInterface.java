package net.kencochrane.raven.event.interfaces;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The Message interface for Sentry allows to send the original pattern to Sentry, allowing the events to be grouped
 * by original message (rather than the formatted version).
 * <p>
 * Sentry's ability to regroup event with the same messages is based on the content of the message, meaning that an
 * {@link net.kencochrane.raven.event.Event} with the message "<cite>User1 failed to provide an email address</cite>"
 * won't be grouped with an Event with the message "<cite>User2 failed to provide an email address</cite>".
 * </p>
 * <p>
 * To allow this kind of grouping, sentry supports the message interface which will provide both the pattern of the
 * message and the parameters. In this example the pattern could be:<br />
 * <cite>{} failed to provide an email address</cite><br />
 * And the parameters would be <cite>User1</cite> in the first Event and <cite>User2</cite> in the second Event.<br />
 * This way, Sentry will be able to put the two events in the same category.
 * </p>
 * <p>
 * Note: Sentry won't attempt to format the message, this is why the formatted message should be set through
 * {@link net.kencochrane.raven.event.EventBuilder#setMessage(String)} in any case.
 * </p>
 */
public class MessageInterface implements SentryInterface {
    /**
     * Name of the message interface in Sentry.
     */
    public static final String MESSAGE_INTERFACE = "sentry.interfaces.Message";
    private final String message;
    private final List<String> parameters;

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

    /**
     * Creates a parametrised message for an {@link net.kencochrane.raven.event.Event}.
     *
     * @param message original message.
     * @param params  parameters of the message.
     */
    public MessageInterface(String message, String... params) {
        this(message, Arrays.asList(params));
    }

    /**
     * Creates a parametrised message for an {@link net.kencochrane.raven.event.Event}.
     *
     * @param message    original message.
     * @param parameters parameters of the message.
     */
    public MessageInterface(String message, List<String> parameters) {
        this.message = message;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    @Override
    public String getInterfaceName() {
        return MESSAGE_INTERFACE;
    }

    public String getMessage() {
        return message;
    }

    public List<String> getParameters() {
        return parameters;
    }
}
