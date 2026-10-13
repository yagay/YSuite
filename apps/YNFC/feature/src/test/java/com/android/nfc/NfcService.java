package com.android.nfc;

public final class NfcService {
    private static final NfcService SINGLETON = new NfcService();
    public static NfcService getInstance() { return SINGLETON; }
    public boolean isNfcEnabled() { return true; }
    public void applyRouting(boolean force) { }
    NfcDiscoveryParameters computeDiscoveryParameters(int mask) {
        return new NfcDiscoveryParameters();
    }
}
