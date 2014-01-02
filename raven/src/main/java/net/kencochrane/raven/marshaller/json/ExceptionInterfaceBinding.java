package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ExceptionWithStackTrace;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;

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
     * </p>
     *
     * @param stackTraceInterfaceBinding InterfaceBinding allowing to send a {@link StackTraceInterface} on the JSON
     *                                   stream.
     */
    public ExceptionInterfaceBinding(InterfaceBinding<StackTraceInterface> stackTraceInterfaceBinding) {
        this.stackTraceInterfaceBinding = stackTraceInterfaceBinding;
    }

    @Override
    public void writeInterface(JsonGenerator generator, ExceptionInterface exceptionInterface) throws IOException {
        Deque<ExceptionWithStackTrace> exceptions = exceptionInterface.getExceptions();

        generator.writeStartArray();
        for (Iterator<ExceptionWithStackTrace> iterator = exceptions.descendingIterator(); iterator.hasNext(); ) {
            writeException(generator, iterator.next());
        }
        generator.writeEndArray();
    }

    /**
     * Outputs an exception with its StackTrace on a JSon stream.
     *
     * @param generator JSonGenerator.
     * @param ewst      Exception with its associated {@link StackTraceInterface}.
     * @throws IOException
     */
    private void writeException(JsonGenerator generator, ExceptionWithStackTrace ewst) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(TYPE_PARAMETER, ewst.getExceptionClassName());
        generator.writeStringField(VALUE_PARAMETER, ewst.getExceptionMessage());
        generator.writeStringField(MODULE_PARAMETER, ewst.getExceptionPackageName());
        generator.writeFieldName(STACKTRACE_PARAMETER);
        stackTraceInterfaceBinding.writeInterface(generator, ewst.getStackTraceInterface());
        generator.writeEndObject();
    }

}
