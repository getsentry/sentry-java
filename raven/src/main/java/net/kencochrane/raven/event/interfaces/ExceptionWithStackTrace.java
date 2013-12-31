package net.kencochrane.raven.event.interfaces;

/**
 * Class associating an exception to its {@link net.kencochrane.raven.event.interfaces.StackTraceInterface}.
 */
public final class ExceptionWithStackTrace {

    private static final String DEFAULT_PACKAGE_NAME = "(default)";

    private final String exceptionMessage;
    private final String exceptionClassName;
    private final String exceptionPackageName;
    private final StackTraceInterface stackTraceInterface;

    /**
     * Creates a new instance from the given exceptions data.
     *
     * @param exceptionMessage the message of the exception
     * @param exceptionClassName the exception's class name
     * @param exceptionPackageName the exception's package name
     * @param stackTraceInterface the stack trace interface holding the stack trace information of the exception
     */
    public ExceptionWithStackTrace(
            final String exceptionMessage,
            final String exceptionClassName,
            final String exceptionPackageName,
            final StackTraceInterface stackTraceInterface) {

        this.exceptionMessage = exceptionMessage;
        this.exceptionClassName = exceptionClassName;
        this.exceptionPackageName = exceptionPackageName;
        this.stackTraceInterface = stackTraceInterface;
    }

    /**
     * Gets the exception message.
     *
     * @return the exception message
     */
    public String getExceptionMessage() {
        return exceptionMessage;
    }

    /**
     * Gets the exception's class name.
     *
     * @return the exception's class name
     */
    public String getExceptionClassName() {
        return exceptionClassName;
    }

    /**
     * Gets the exception's package name.
     *
     * @return the exception's package name
     */
    public String getExceptionPackageName() {

        if (exceptionPackageName == null) {
            return DEFAULT_PACKAGE_NAME;
        }

        return exceptionPackageName;
    }

    /**
     * Gets the exceptions stack trace interface.
     *
     * @return the exceptions stack trace interface
     */
    public StackTraceInterface getStackTraceInterface() {
        return stackTraceInterface;
    }

    /**
     * Gets the exception's stack trace.
     *
     * @return the exception's stack trace
     */
    public StackTraceElement[] getStackTrace() {
        return stackTraceInterface.getStackTrace();
    }
}
