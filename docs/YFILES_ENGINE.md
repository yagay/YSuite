# YFiles Engine

YFiles is a provider-based file manager.

The design is inspired by the mature multi-filesystem separation used by open-source Android file
managers such as Material Files, while the YSuite implementation is independently written.

## Layers

```
YFiles UI
  -> YFilesEngine
     -> YFileProviderRegistry
        -> Local provider
        -> Document / SAF provider
        -> Root provider
        -> Archive provider
        -> Remote providers
```

The UI depends only on `YFilesEngine`.

## Provider contract

Each provider owns:
- its root reference,
- path parent semantics,
- listing and search,
- metadata,
- create / rename / delete,
- copy / move within that provider,
- an explicit capability set.

A `YFileRef` is always namespaced by `providerId`; raw paths are never assumed to be globally
interchangeable.

## Transfer model

Same-provider copy/move is implemented by the provider.

Cross-provider transfer is deliberately rejected by the first engine revision. A later transfer
coordinator will stream data through provider read/write contracts so Local <-> SAF <-> Root <->
Archive/Remote can share one operation pipeline.

## Next providers

The next implementation order is:
1. Document / SAF,
2. Root,
3. Archive,
4. Remote (SMB/SFTP/WebDAV).

This keeps Android permission and privileged-access concerns out of the browser UI.
