package io.sentry.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import io.sentry.event.interfaces.ExceptionInterface;
import io.sentry.event.interfaces.SentryException;
import io.sentry.event.interfaces.StackTraceInterface;

import java.io.IOException;
import java.util.Deque;
import java.util.Iterator;

/**
 * Binding system allowing to convert an {@link ExceptionInterface} to a JSON stream.
 */
public class ExceptionInterfaceBinding implements InterfaceBinding<ExceptionInterface> {
    private static final String TYPE_PARAMETER = "type";
    private static final String VALUE_PARAMETER = "value";
    private static final String MODULE_PARAMETER = "module";
    private static final String STACKTRACE_PARAMETER = "stacktrace";
    private final InterfaceBinding<StackTraceInterface> stackTraceInterfaceBinding;

    /**
     * Creates a Binding system to send a {@link ExceptionInterface} on JSON stream.
     * <p>
     * Exceptions may contain StackTraces, this means that the system should also be able to send a
     * {@link StackTraceInterface} on the JSON stream.
     *
     * @param stackTraceInterfaceBinding InterfaceBinding allowing to send a {@link StackTraceInterface} on the JSON
     *                                   stream.
     */
    public ExceptionInterfaceBinding(InterfaceBinding<StackTraceInterface> stackTraceInterfaceBinding) {
        this.stackTraceInterfaceBinding = stackTraceInterfaceBinding;
    }

    @Override
    public void writeInterface(JsonGenerator generator, ExceptionInterface exceptionInterface) throws IOException {
        Deque<SentryException> exceptions = exceptionInterface.getExceptions();

        generator.writeStartArray();
        for (Iterator<SentryException> iterator = exceptions.descendingIterator(); iterator.hasNext(); ) {
            writeException(generator, iterator.next());
        }
        generator.writeEndArray();
    }

    /**
     * Outputs an exception with its StackTrace on a JSon stream.
     *
     * @param generator       JSonGenerator.
     * @param sentryException Sentry exception with its associated {@link StackTraceInterface}.
     * @throws IOException
     */
    private void writeException(JsonGenerator generator, SentryException sentryException) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(TYPE_PARAMETER, sentryException.getExceptionClassName());
        generator.writeStringField(VALUE_PARAMETER, sentryException.getExceptionMessage());
        generator.writeStringField(MODULE_PARAMETER, sentryException.getExceptionPackageName());
        generator.writeFieldName(STACKTRACE_PARAMETER);
        stackTraceInterfaceBinding.writeInterface(generator, sentryException.getStackTraceInterface());
        generator.writeEndObject();
    }

}
