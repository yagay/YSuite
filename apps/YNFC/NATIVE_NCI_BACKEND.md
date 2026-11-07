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
| `android.hardware.nfc2-service.nxp` | `7939ece2dcb8111560b3e5dbd278f18b3a83a2b60a325b4e07d92a6b305bcde2` | Android 36 launcher, linked against the separate SNxxx HAL implementation |
| `nfc_nci_nxp_snxxx_v2.so` | `415152acf97d4731dafe370d8bb4e136a7929ce04a7255023f03f3cb60daea22` | **Now received**; ARM64 Android 36, Build ID `af894d508967190bd6a2a1d8ef139d37`; exports `CNfcConfig::readConfig`, `readNxpRFConfig`, `getValue`, `NfcWriter::write`, `phNxpNciHal_write` |
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

**HAL implementation now provided:** `nfc_nci_nxp_snxxx_v2.so`
contains the actual `phNxpNciHal_write` and `NfcWriter::write`
functions absent from the service launcher. This fixes the missing-file
gap, **not** the unverified controller-UID-write gap.

Static strings and symbols confirm that this NXP HAL supports the
`CNfcConfig::readConfig()` / `readNxpRFConfig()` chain and explicitly
references `/data/vendor/nfc/libnfc-mtp-SN220.conf`, `NXP_CORE_CONF`,
`NXP_CORE_CONF_EXTN`, `NXP_RF_CONF_BLK_` and `NXP_SET_CONFIG_ALWAYS`.
The `readNxpRFConfig()` AArch64 implementation forwards into
`readConfig()`. It does not establish which specific file was selected
during the observed device initialization.

The 13 user-supplied `libnfc-mtp-SN220.conf_*` versions have the same
`NXP_CORE_CONF` byte sequence, including `LA_NFCID1` `33 00`
(zero-length configuration). The `NXP_SET_CONFIG_ALWAYS` flag is
`0x01` for only the 24624/24776 files and `0x00` for the other 11.
Consequently, replacing a file alone does not prove a new NCI config
will be issued: the HAL also tracks configuration state.

The proposed **first passive observation boundary** is the
`CNfcConfig::getValue()` retrieval of `NXP_CORE_CONF` inside the
separate vendor HAL process, correlated with `phNxpNciHal_write()`,
NCI TX/RX and controller initialization. Observe first; don't
change the values or substitute configuration files yet.

Recommended passive investigation order:
1. Confirm the actual `libnfc_nci_jni.so` mapping in `com.android.nfc`,
   plus its full-file SHA-256.
2. Observe `NFA_SetConfig`, `nci_snd_core_set_config` and
   `NfcAdaptation::HalWrite` **without modification** and correlate with
   NCI TX, the command generation, and NFC process PID.
3. Compare the newly supplied `nfc_nci_nxp_snxxx_v2.so` SHA-256
   against the actual loaded HAL and verify the config file chosen on device.
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

## Download/YSuite/YNFC/ automatic snapshots

On selected-card Simulate, file preparation happens *before* the NFC Hook
readiness check. A stale/missing Hook therefore does not prevent safe file
generation, but it still prevents publishing an APPLY command.

Files saved using the Android MediaStore Downloads API (no storage picker or
broad storage permission):

- `selected-nfc-a.json` – automatically generated UID / ATQA / SAK profile;
  application-private copy remains the canonical input sent to ConfigProvider.
- `original-SN220.conf` – raw bytes copied with Root from the exact source
  `/data/vendor/nfc/libnfc-mtp-SN220.conf`, never from a guessed ODM revision.
- `modified-SN220.conf` – **offline preview only**, copied from that exact
  source. The standalone `NXP_CORE_CONF` is strictly parsed as a complete
  NCI CORE_SET_CONFIG packet. The preview updates only the existing, uniquely
  identified zero-length `LA_NFCID1` (`0x33 00`) parameter and the NCI
  payload-length count. All other configuration keys remain unchanged.
- `config-status.txt` – PREPARING, FAILED, PROFILE_EXPORTED or
  PREVIEW_ONLY_NATIVE_WRITE_DISABLED, including source/hash notes and a
  warning when older preview files should not be trusted.
- `YNFC-diagnostic_*.txt` – exported program diagnostics under the same folder.

The parser refuses an absent or duplicate `NXP_CORE_CONF`, malformed NCI
lengths or TLVs, non-empty stock `LA_NFCID1`, invalid UID sizes, oversized
inputs and non-lossless text decoding. Source and modified SHA-256 are recorded
in the status file and diagnostic report. Failure blocks new APPLY commands.

**Important:** MediaStore files are convenient exports, **not** the HAL
runtime configuration. The NFC HAL process generally cannot open shared
Downloads without appropriate SELinux authorization. No HAL hook is currently
installed; YNFC does not modify `/data/vendor/nfc`, mount/replace a config
file, send new native NCI commands or reboot the phone. Do not infer UID
simulation from successful file creation. The user has explicitly requested
these values in a publicly visible folder; note that NFC UID/card metadata
in Downloads may be readable by other applications or backup tools.

## Read-only HAL config loading check (on-demand)

The independent `NATIVE` mode now includes the **"Inspect HAL config
loading (read-only)"** button / **"检查 HAL 配置加载（只读）"**.
It runs on a background worker and writes a dedicated
`Download/YSuite/YNFC/YNFC-hal-config-probe_*.txt` report.

This check does **not** restart the NFC service, patch libraries, redirect
a file, call a vendor Binder interface, or send an NCI command.

It samples the real `android.hardware.nfc2-service.nxp` process and other
OPLUS NFC services, the libraries in `/proc/<pid>/maps`, NFC config paths
visible in `/proc/<pid>/fd`, the SHA-256 of candidate configuration and
`nfc_nci_nxp_snxxx_v2.so` files, plus bounded **counts only** of matching
NFC initialization log keywords. No raw NFC packets or UID log contents
are included.

Evidence levels in the resulting report:

- `ROOT_PROBE_UNAVAILABLE`: the command did not run with root credentials.
- `NXP_HAL_PROCESS_NOT_OBSERVED`: the expected NXP service PID is missing
  at sampling time. OPLUS service presence is not a substitute.
- `SN220_CONFIG_OPEN_FD_OBSERVED`: the specific NXP HAL PID actually
  had an SN220 config FD open during a sample. It may or may not have
  consumed `NXP_CORE_CONF`; an open FD alone does not prove that.
- `SN220_CONFIG_OPEN_NOT_OBSERVED`: the HAL is running but no matching
  file descriptor was caught; the file may have been read and closed long
  before this button was pressed.

The target file and a matching HAL checksum merely establish candidates.
`CNfcConfig::readNxpRFConfig()` and `CNfcConfig::getValue()` are **not yet
hooked or traced**; the passive FD survey does not pretend otherwise.
A native read interception implementation remains contingent on exact
device ABI, SELinux scope and verified cleanup semantics.

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

1. Match the uploaded ELF files, including the newly provided
   `nfc_nci_nxp_snxxx_v2.so`, against the loaded device libraries.
   YNFC now reports the SNxxx SHA-256 and whether it matches the
   uploaded binary; a match is not proof of a controllable UID.
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
