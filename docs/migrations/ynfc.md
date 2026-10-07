# Feature migration: YNFC

Product surface: Tool
Upstream layout reference: nfcgate/nfcgate (Apache-2.0)

YNFC keeps YSuite-owned NFC-A scanning, saved access-card profiles, one-tap switching, API-102
com.android.nfc RF Hooking, Root NFC-process restart, stock-RF recovery, runtime verification and
diagnostic export. NFCGate is used only for the status/mode/workspace layout.

Payment HCE routing is not replaced or disabled. Access-card simulation changes the controller RF
UID configuration path and STOP restores stock RF through the NFC process lifecycle.
