# YNFC backend separation (ColorOS / OPlus)

## Current implementation on `test`

- **Existing RF backend:** NfcInjectionModule / RfReplayEngine still owns the
  live card commands and restores its captured stock RF configuration. Hook
  installation or a share-mode response is *not* RF proof.
- **Vendor Type-A backend:** NfcProcessVendorController now exposes
  `probeTypeA()` (read-only) and `setTypeAConfig(enabled, atqa, sak, uid)`.
  The direct method requires explicit 2-byte ATQA, 1-byte SAK and a 4/7/10-byte
  UID, a verified `IVendorNfcAdapter` Binder descriptor, and a callable OEM
  Java/AIDL method. It does not guess transaction 16.
- **Selection:** not enabled for user commands yet. The Provider already persists explicit UID/ATQA/SAK. HookConfigStore and
  SimConfig now retain all three values; a serialized arbitration/verification
  path is still required before enabling direct writes. At initialization
  `TYPE_A_CAPABILITY` reports the callable method and logs
  `selectedBackend=RF_HOOK`. Until explicit parameters, backend arbitration
  and card restore have been integrated, direct Type-A calls must not run
  alongside automatic RF rewriting.

## Interpretation

- `TYPE_A_API_PRESENT` means only that the method is accessible.
- `TYPE_A_API_ACCEPTED` means only that the OEM API returned true.
- Neither means the NFC controller committed the RF settings, nor that a reader
  recognized the desired card.
- An NFCC model string and the vendor Binder descriptor do not certify the
  complete OEM AIDL transaction table. Do not infer transaction 15/16 from the
  model alone.
- NFC payment and eSE routing must not be changed by unverified emulation.
- An explicit STOP must restore the selected backend's own state before trying
  another backend. Do not switch engines mid-generation.

## Remaining integration

1. Validate the already-persisted ATQA/SAK fields at the controller boundary and preserve them in backup.
2. Route APPLY / STOP through one serialized backend coordinator per generation.
3. Verify vendor confirmation and actual RF behavior; implement safe rollback,
   controller lifecycle recovery and app UI status.
4. Add unit/integration tests, debug compilation and hardware reader tests.
5. Preserve standard APDU HCE as a separate capability, not equivalent to
   static Type-A UID/SAK/ATQA settings.

## Research

- https://gist.github.com/PeronGH/8e272a007edfc192c0aff8d3a6b187da
- https://github.com/skb8/OplusNfcTool

Note: the reverse-engineering reference covers a specific ColorOS 16 NXP stack;
it does not establish compatibility with ST/Thales or other ROM revisions.
