package net.kencochrane.raven.event.interfaces;

import java.util.HashMap;
import java.util.Map;

public class ExceptionInterface implements SentryInterface {
    private static final String EXCEPTION_INTERFACE = "sentry.interfaces.Exception";
    private static final String TYPE_PARAMETER = "type";
    private static final String VALUE_PARAMETER = "value";
    private static final String MODULE_PARAMETER = "module";
    private final Throwable throwable;

    public ExceptionInterface(Throwable throwable) {
        this.throwable = throwable;
    }

    @Override
    public String getInterfaceName() {
        return EXCEPTION_INTERFACE;
    }

    @Override
    public Map<String, Object> getInterfaceContent() {
        Map<String, Object> content = new HashMap<String, Object>();
        content.put(TYPE_PARAMETER, throwable.getClass().getSimpleName());
        // TODO: The message can be empty here, should something be done?
        content.put(VALUE_PARAMETER, throwable.getMessage());
        content.put(MODULE_PARAMETER, throwable.getClass().getPackage().getName());
        return content;
    }
}
