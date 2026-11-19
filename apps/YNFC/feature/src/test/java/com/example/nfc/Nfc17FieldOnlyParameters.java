package com.example.nfc;

/**
 * Exact field contract from NFC Service 17's NfcDiscoveryParameters DEX.
 * This class intentionally has NO getTechMask() or host-routing getter.
 */
public final class Nfc17FieldOnlyParameters {
    public boolean mEnableHostRouting = true;
    public boolean mEnableLowPowerDiscovery = true;
    public boolean mEnableReaderMode = false;
    public byte[] mExtraAnnotation = new byte[]{7, 8};
    public byte[] mTechAPollingLoopAnnotation = new byte[]{1, 2};
    public int mTechMask = 0x2f;
    public Nfc17FieldOnlyParameters() {}
    public boolean shouldEnableDiscovery() {
        return mTechMask != 0 || mEnableHostRouting || mEnableReaderMode;
    }
}