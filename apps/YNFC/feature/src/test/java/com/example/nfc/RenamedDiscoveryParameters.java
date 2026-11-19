package com.example.nfc;

/** No no-arg constructor and no mTechMask backing field: Builder is mandatory. */
public final class RenamedDiscoveryParameters {
    private final int obfuscatedTech;
    private final boolean hce;
    private final boolean lowPower;
    private final boolean reader;
    private final byte[] annotation;

    private RenamedDiscoveryParameters(int tech, boolean hce, boolean lowPower,
                                       boolean reader, byte[] annotation) {
        this.obfuscatedTech = tech;
        this.hce = hce;
        this.lowPower = lowPower;
        this.reader = reader;
        this.annotation = annotation;
    }
    public static Builder newBuilder() { return new Builder(); }
    public int getTechMask() { return obfuscatedTech; }
    public boolean shouldEnableHostRouting() { return hce; }
    public boolean shouldEnableLowPowerDiscovery() { return lowPower; }
    public boolean shouldEnableReaderMode() { return reader; }
    public byte[] techAPollingLoopAnnotation() { return annotation; }

    public static final class Builder {
        private int tech = 0x2f;
        private boolean hce = true;
        private boolean lowPower = true;
        private boolean reader = false;
        private byte[] annotation = {3, 4};
        public Builder setTechMask(int value) { tech = value; return this; }
        public Builder setEnableHostRouting(boolean value) { hce = value; return this; }
        public Builder setEnableLowPowerDiscovery(boolean value) { lowPower = value; return this; }
        public Builder setEnableReaderMode(boolean value) { reader = value; return this; }
        public Builder setTechAPollingLoopAnnotation(byte[] value) {
            annotation = value;
            return this;
        }
        public RenamedDiscoveryParameters build() {
            return new RenamedDiscoveryParameters(tech, hce, lowPower, reader, annotation);
        }
    }
}
