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

## Native ELF investigation from the six supplied files (2026-11-07)

All six are stripped AArch64 Android ELFs. These observations are static
and must not be mistaken for proof that the corresponding function was called
on the device.

| Uploaded file | SHA-256 | Evidence |
| --- | --- | --- |
| `libnfc_nci_jni.so` | `8b535f81e6f9b99e8773e9fae1cc1b9e5a9c1d5492b3da22ee130d2d4e98d2ac` | Android 37; `JNI_OnLoad`, `NFA_SetConfig`, `nci_snd_core_set_config`, `NfcAdaptation::HalWrite`, `NfcVendorExtn::processCmd` |
| `libnfc-nci.so` | `368818d9d3a8583ea7c24604dc7517594d254976cd1f3c297e8b64e46969469a` | Android 37; **separate copy** of NFA and NCI config symbols |
| `libnfc_vendor_extn.so` | `2b8dffc47296fbcc95c9a409bfcfcb897a720414ae383e6c8ac8ed4d8fb8e6d9` | Android 37; `CNfcConfig::readNxpRFConfig`, `RfConfigManager::sendNciCmd`, `RfConfigManager::updateRfSetConfig` |
| `android.hardware.nfc2-service.nxp` | `7939ece2dcb8111560b3e5dbd278f18b3a83a2b60a325b4e07d92a6b305bcde2` | Android 36 launcher, **NEEDED: `nfc_nci_nxp_snxxx_v2.so`**, undefined `phNxpNciHal_write` |
| `vendor.oplus.hardware.nfc_aidl-service` | `1d5d2bf7c39a570c0a6962f2f52d56e0e56ecbf007c823a2f1aaec12c8767f4a` | OPLUS AIDL launcher; NEEDED: `vendor.oplus.hardware.nfc_aidl-V3-ndk.so` |
| `vendor.oplus.hardware.nfcExtns-service` | `3f0b041b3dafce8f53ed91cf6143bd7916c969061ed88d9ead5d345e0a5bbb21` | NFC extension launcher; NEEDED: `libpnscr2_aidl.so` |

The active Java NFC process's previous `/proc/<pid>/maps` inventory showed
`/system/lib64/libnfc_nci_jni.so`, not `libnfc-nci.so`.
The JNI binary does not declare `libnfc-nci.so` as a `DT_NEEDED` dependency;
it contains its own copy of the NCI stack. Consequently, an arbitrary hook
against `libnfc-nci.so` may never execute in the user's NFC process.

AArch64 disassembly of **the uploaded JNI library** confirms:
- `nci_snd_core_set_config(uint8_t*, uint8_t)` constructs the NCI
  `CORE_SET_CONFIG` command and queues it.
- `NfcAdaptation::HalWrite(uint16_t,uint8_t*)` invokes
  `NfcVendorExtn::processCmd` and forwards NCI command bytes to an NFC
  HAL interface.
- `NFA_SetConfig(uint8_t,uint8_t,uint8_t*)` queues the NFA config event.

These are useful **observation boundaries**, not a validated UID-rewrite API.
The JNI binary also exports `updateNfcID0Param`, but NFCID0 is not proof
of the NFC-A `LA_NFCID1` parameter; do not conflate the identifiers.

**Critical missing library:** `nfc_nci_nxp_snxxx_v2.so` is the HAL
implementation required by `android.hardware.nfc2-service.nxp`.
The uploaded service executable is a thin wrapper and does not contain the
referenced `phNxpNciHal_write` implementation. Extract the exact loaded
library from the same firmware and compare its SHA-256 and build ID before
any native adapter can be designed.

Recommended passive investigation order:
1. Confirm the actual `libnfc_nci_jni.so` mapping in `com.android.nfc`,
   plus its full-file SHA-256.
2. Observe `NFA_SetConfig`, `nci_snd_core_set_config` and
   `NfcAdaptation::HalWrite` **without modification** and correlate with
   NCI TX, the command generation, and NFC process PID.
3. Obtain `nfc_nci_nxp_snxxx_v2.so` and inspect its HAL-level write path.
4. Only consider validated, firmware-specific NFCID1 handling and a safe
   restore path; do not blindly inject `LA_NFCID1` into unrelated RF payloads.
5. Verify the actual presented UID using an external reader on an authorized
   test tag, not by interpreting JNI/HAL function return values.

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

1. Match the uploaded ELF files against the SHA-256 from this device's latest
   diagnostic export. Obtain the additional **required** implementation
   `nfc_nci_nxp_snxxx_v2.so` from the same installed firmware. The launcher
   `android.hardware.nfc2-service.nxp` is not sufficient.
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
