package io.sentry.event.interfaces;

/**
 * Richer StackTraceElement class.
 */
public class SentryStackTraceElement {
    private final String fileName;
    private final String function;
    private final String module;
    private final int lineno;
    private final int colno;
    private final String absPath;
    private final String platform;

    /**
     * Construct a SentryStackTraceElement.
     *
     * @param fileName Filename.
     * @param function Function name.
     * @param module Module name.
     * @param lineno Line number.
     * @param colno Column number.
     * @param absPath Absolute path.
     * @param platform Platform name.
     */
    public SentryStackTraceElement(String fileName, String function, String module, int lineno,
                                   int colno, String absPath, String platform) {
        this.fileName = fileName;
        this.function = function;
        this.module = module;
        this.lineno = lineno;
        this.colno = colno;
        this.absPath = absPath;
        this.platform = platform;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFunction() {
        return function;
    }

    public String getModule() {
        return module;
    }

    public int getLineno() {
        return lineno;
    }

    public int getColno() {
        return colno;
    }

    public String getAbsPath() {
        return absPath;
    }

    public String getPlatform() {
        return platform;
    }
}
