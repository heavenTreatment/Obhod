package com.heaventreat.obhod.bridge;

public final class NativeBridge {
    private static volatile boolean loaded;

    public static synchronized void load() {
        if (loaded) return;
        System.loadLibrary("hev_tunnel");
        loaded = true;
    }

    public static boolean isLoaded() { return loaded; }

    public static native boolean startTunnel(int tunFd, String configPath);
    public static native boolean isTunnelRunning();
    public static native void stopTunnel();

    private NativeBridge() {}
}
