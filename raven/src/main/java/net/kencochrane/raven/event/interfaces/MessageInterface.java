package net.kencochrane.raven.event.interfaces;

import java.util.*;

public class MessageInterface implements SentryInterface {
    private static final String MESSAGE_INTERFACE = "sentry.interfaces.Message";
    private static final String MESSAGE_PARAMETER = "message";
    private static final String PARAMS_PARAMETER = "params";
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

    @Override
    public Map<String, Object> getInterfaceContent() {
        Map<String, Object> content = new HashMap<String, Object>();
        content.put(MESSAGE_PARAMETER, message);
        if (!params.isEmpty())
            content.put(PARAMS_PARAMETER, params);

        return content;
    }
}
