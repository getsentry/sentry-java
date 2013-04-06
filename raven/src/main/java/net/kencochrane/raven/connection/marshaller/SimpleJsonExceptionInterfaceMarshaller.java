package net.kencochrane.raven.connection.marshaller;

import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.SentryInterface;
import org.json.simple.JSONObject;

public class SimpleJsonExceptionInterfaceMarshaller implements SimpleJsonInterfaceMarshaller {
    private static final String TYPE_PARAMETER = "type";
    private static final String VALUE_PARAMETER = "value";
    private static final String MODULE_PARAMETER = "module";

    @Override
    public JSONObject serialiseInterface(SentryInterface sentryInterface) {
        if (!(sentryInterface instanceof ExceptionInterface)) {
            //TODO: Do something better here!
            throw new IllegalArgumentException();
        }

        ExceptionInterface messageInterface = (ExceptionInterface) sentryInterface;
        JSONObject jsonObject = new JSONObject();
        Throwable throwable = messageInterface.getThrowable();
        jsonObject.put(TYPE_PARAMETER, throwable.getClass().getSimpleName());
        jsonObject.put(VALUE_PARAMETER, throwable.getMessage());
        jsonObject.put(MODULE_PARAMETER, throwable.getClass().getPackage().getName());
        return jsonObject;
    }
}
