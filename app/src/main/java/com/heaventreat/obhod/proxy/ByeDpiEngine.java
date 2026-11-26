package com.heaventreat.obhod.proxy;

public final class ByeDpiEngine {
    public interface LogSink {
        void onLog(int priority, String tag, String message);
    }

    static {
        System.loadLibrary("byedpi");
    }

    public static native void setLogSink(LogSink sink);
    public static native int startProxy(String[] args);
    public static native void stopProxy();

    private ByeDpiEngine() {}
}
