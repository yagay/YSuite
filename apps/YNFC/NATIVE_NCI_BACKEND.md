# YNFC independent native NCI/HAL path (ColorOS 17, SN220T)

## Scope and verified evidence

- The inspected `NFC Service_17.0.0.260114.apk` contains two DEX files and the
  `assets/nfc_rf_config_params.xml` / `assets/oplus_nfc_feature.xml` files.
  It contains **no native NXP HAL ELF libraries**.
- The OEM `HceAccessCard.doSetHceTypeAConfig()` callback seen in the user's log
  happened with `enable=false` and absent ATQA/SAK/UID byte arrays.
  This is not evidence of an eligible NFC-A UID-configuration call.
- The NXP HAL runs in `android.hardware.nfc2-service.nxp`, with an additional
  `vendor.oplus.hardware.nfc_aidl-service` process. The existing Java
  `NativeNfcManager#setNciConfig()` hook is installed, but its UID writer was
  **not observed** during active attempts.
- NCI `CORE_SET_CONFIG` / `LA_NFCID1` is a candidate investigation path,
  not proof this SN220T firmware permits arbitrary on-air NFCID1.

## Architecture

`saved card -> private selected-nfc-a.json -> ConfigProvider backend=NATIVE
-> NfcInjectionModule native preflight -> firmware-specific native adapter (not enabled)`

The private JSON file only stores validated card data. NFC HAL processes do not
read the app-private file. A proven firmware-specific interface would receive
validated parameters through the existing narrow Provider/Hook boundary.

The `NATIVE` mode is independent of `INTERCEPT` (OEM HCE), `VENDOR`
(privileged ColorOS API), and legacy `RF` (Java NCI hook).
It **never silently falls back** to those implementations.

## Current behavior

- `NATIVE` is the initial preferred mode for installations without an explicit
  saved backend choice; existing user selections remain unchanged.
- On APPLY, YNFC automatically generates and validates the private NFC-A file.
- The Hook returns `FAILED / NATIVE_HAL_ABI_UNVERIFIED` without submitting any
  NCI packet or modifying the NFC controller.
- STOP after no write returns `NO_OVERRIDE` without restarting Android.
- Debug export records SHA-256 hashes for actual `libnfc-*.so` / HAL
  executables and loaded NFC library paths, plus NCI packet timing evidence.
  It does not dump private firmware contents or mutate the device.
- LSPosed protocol `HOOK_BUILD=46` remains stable. Implementation revision
  `3` identifies the new native routing in a running NFC PID.
- Only the existing arm64 Debug workflow is used; no Magisk ZIP or Release.

## Before a native UID writer can be enabled

1. Obtain the **exact** binaries from this device/firmware:
   `/system/lib64/libnfc-nci.so`,
   `/system/lib64/libnfc_nci_jni.so`,
   `/system/lib64/libnfc_vendor_extn.so`,
   `/odm/bin/hw/android.hardware.nfc2-service.nxp` and
   `/odm/bin/hw/vendor.oplus.hardware.nfc_aidl-service`.
2. Compare SHA-256 with the diagnostic inventory; inspect exported symbols,
   JNI calling conventions, and on-device NCI transaction lifecycle.
3. Verify whether the controller accepts a unique, correctly formed NFCID1
   setting under a controlled, user-owned test setup.
4. Add a strictly isolated native adapter with a rollback path and
   firmware-specific enable check; if the binary/ABI differs, fail closed.
5. Validate the **actual on-air UID** using an external NFC reader.
   Successful Java/native returns, Hook installation, or JSON creation alone
   are not proof of RF emulation.

Do not replace `libnfc-nci.conf`, inject an unverified `.so`, grant OEM
signature permissions, or spoof a successful controller response to skip these
checks. Android HCE APDUs are a separate, supported feature; they do not provide
generic control over the anti-collision NFCID1.
