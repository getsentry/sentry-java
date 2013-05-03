package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

public class ExceptionInterfaceBinding implements InterfaceBinding<ExceptionInterface> {
    private static final Logger logger = Logger.getLogger(ExceptionInterfaceBinding.class.getCanonicalName());
    private static final String TYPE_PARAMETER = "type";
    private static final String VALUE_PARAMETER = "value";
    private static final String MODULE_PARAMETER = "module";
    private static final String STACKTRACE_PARAMETER = "stacktrace";
    private final InterfaceBinding<StackTraceInterface> stackTraceInterfaceBinding;

    public ExceptionInterfaceBinding(InterfaceBinding<StackTraceInterface> stackTraceInterfaceBinding) {
        this.stackTraceInterfaceBinding = stackTraceInterfaceBinding;
    }

    @Override
    public void writeInterface(JsonGenerator generator, ExceptionInterface exceptionInterface) throws IOException {
        Set<ImmutableThrowable> dejaVu = new HashSet<ImmutableThrowable>();
        ImmutableThrowable throwable = exceptionInterface.getThrowable();
        StackTraceElement[] enclosingStackTrace = new StackTraceElement[0];

        generator.writeStartArray();
        while (throwable != null) {
            dejaVu.add(throwable);

            generator.writeStartObject();
            generator.writeStringField(TYPE_PARAMETER, throwable.getActualClass().getSimpleName());
            generator.writeStringField(VALUE_PARAMETER, throwable.getMessage());
            generator.writeStringField(MODULE_PARAMETER, throwable.getActualClass().getPackage().getName());
            generator.writeFieldName(STACKTRACE_PARAMETER);
            stackTraceInterfaceBinding.writeInterface(generator,
                    new StackTraceInterface(throwable.getStackTrace(), enclosingStackTrace));
            generator.writeEndObject();
            enclosingStackTrace = throwable.getStackTrace();
            throwable = throwable.getCause();

            if (dejaVu.contains(throwable)) {
                logger.warning("Exiting a circular referencing exception!");
                break;
            }
        }
        generator.writeEndArray();
    }
}
