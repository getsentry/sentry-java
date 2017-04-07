package io.sentry.event.interfaces;

public class SentryStackTraceElement {

    private final String fileName;
    private final String function;
    private final String module;
    private final int lineno;
    private final int colno;
    private final String absPath;
    private final String platform;

    public SentryStackTraceElement(String fileName, String function, String module, int lineno, int colno, String absPath, String platform) {
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
