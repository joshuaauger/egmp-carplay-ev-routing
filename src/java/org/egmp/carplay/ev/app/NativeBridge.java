package org.egmp.carplay.ev.app;
/** SPDX-License-Identifier: GPL-3.0-or-later
 * Only loaded in Iap2ServerApp, after exact library-hash verification.
 * Byte arrays carry standard UTF-8, avoiding JNI modified-UTF-8 strings.
 */
public final class NativeBridge {
    private NativeBridge() {}
    public static void load(String path){System.load(path);}
    public static native int open();
    public static native void reset();
    public static native void shutdown();
    public static native int send(long clientHandle, int[] sample, byte[] charging, byte[] consumption);
    public static native int stop(long clientHandle);
    /** The encoded CarPlayStartSession (0x4301) to answer CarPlayAvailability with; empty for off. */
    public static native int startSession(byte[] message);
    /** 0 offers seen, 1 answers sent, 2 last send result, 3 last offer length, 16+i its parameter bytes */
    public static native int startSessionStatus(int which);
}
