package net.kencochrane.raven.marshaller.json;

import com.fasterxml.jackson.core.JsonGenerator;
import net.kencochrane.raven.event.interfaces.ExceptionInterface;
import net.kencochrane.raven.event.interfaces.ImmutableThrowable;
import net.kencochrane.raven.event.interfaces.StackTraceInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * Binding system allowing to convert an {@link ExceptionInterface} to a JSON stream.
 */
public class ExceptionInterfaceBinding implements InterfaceBinding<ExceptionInterface> {
    private static final Logger logger = LoggerFactory.getLogger(ExceptionInterfaceBinding.class);
    private static final String TYPE_PARAMETER = "type";
    private static final String VALUE_PARAMETER = "value";
    private static final String MODULE_PARAMETER = "module";
    private static final String STACKTRACE_PARAMETER = "stacktrace";
    private static final String DEFAULT_PACKAGE_NAME = "(default)";
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
        Deque<ExceptionWithStackTrace> exceptions = unfoldExceptionInterface(exceptionInterface);

        //Unstack the exceptions
        generator.writeStartArray();
        while (!exceptions.isEmpty()) {
            writeException(generator, exceptions.pop());
        }
        generator.writeEndArray();
    }

    /**
     * Stack the exception and its causes with their StackTraces to provide them in the order expected by Sentry.
     * <p>
     * Sentry expects to get the exception from the first generated (cause) to the last generated. To provide the
     * exceptions in this order, those are stacked then later the stack is emptied.
     * </p>
     * <p>
     * Each exception provides a {@link StackTraceInterface}.
     * </p>
     *
     * @param exceptionInterface Sentry interface containing the captured exception.
     * @return a Stack of Exceptions with their {@link StackTraceInterface}.
     */
    private Deque<ExceptionWithStackTrace> unfoldExceptionInterface(ExceptionInterface exceptionInterface) {
        Deque<ExceptionWithStackTrace> exceptions = new ArrayDeque<ExceptionWithStackTrace>();
        Set<ImmutableThrowable> circularityDetector = new HashSet<ImmutableThrowable>();
        ImmutableThrowable throwable = exceptionInterface.getThrowable();
        StackTraceElement[] enclosingStackTrace = new StackTraceElement[0];

        //Stack the exceptions to send them in the reverse order
        while (throwable != null) {
            if (!circularityDetector.add(throwable)) {
                logger.warn("Exiting a circular exception!");
                break;
            }

            StackTraceInterface stackTrace = new StackTraceInterface(throwable.getStackTrace(), enclosingStackTrace);
            exceptions.push(new ExceptionWithStackTrace(throwable, stackTrace));
            enclosingStackTrace = throwable.getStackTrace();
            throwable = throwable.getCause();
        }

        return exceptions;
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
        generator.writeStringField(TYPE_PARAMETER, ewst.exception.getActualClass().getSimpleName());
        generator.writeStringField(VALUE_PARAMETER, ewst.exception.getMessage());
        Package aPackage = ewst.exception.getActualClass().getPackage();
        generator.writeStringField(MODULE_PARAMETER, (aPackage != null) ? aPackage.getName() : DEFAULT_PACKAGE_NAME);

        generator.writeFieldName(STACKTRACE_PARAMETER);
        stackTraceInterfaceBinding.writeInterface(generator, ewst.stackTrace);
        generator.writeEndObject();
    }

    /**
     * Class associating an exception to its {@link StackTraceInterface}.
     */
    private final class ExceptionWithStackTrace {
        private ImmutableThrowable exception;
        private StackTraceInterface stackTrace;

        private ExceptionWithStackTrace(ImmutableThrowable exception, StackTraceInterface stackTrace) {
            this.exception = exception;
            this.stackTrace = stackTrace;
        }
    }
}
