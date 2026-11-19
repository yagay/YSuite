package com.android.nfc;

/** Fixture matching the APK-verified NFC discovery parameter getter/field contract. */
public final class NfcDiscoveryParameters {
    private int mTechMask = 0x2f;
    private boolean mEnableHostRouting = true;
    private boolean mEnableReaderMode = false;
    private boolean mEnableLowPowerDiscovery = true;
    private byte[] mTechAPollingLoopAnnotation = new byte[]{1, 2};
    public NfcDiscoveryParameters() {}
    public int getTechMask() { return mTechMask; }
    public boolean shouldEnableHostRouting() { return mEnableHostRouting; }
    public boolean shouldEnableReaderMode() { return mEnableReaderMode; }
    public boolean shouldEnableLowPowerDiscovery() { return mEnableLowPowerDiscovery; }
    public byte[] techAPollingLoopAnnotation() { return mTechAPollingLoopAnnotation; }
}
