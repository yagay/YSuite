# YFiles Engine

YFiles is a unified provider-based file manager.

Its architecture is inspired by mature open-source Android file managers such as Material Files and
Amaze File Manager, while all YSuite implementation code is independently written.

## Layers

```
Unified YSuite UI
  -> YFiles ViewModels
     -> YFiles services
        -> YFilesEngine
           -> YFileProviderRegistry
              -> LocalFileProvider
              -> DocumentFileProvider
              -> RootFileProvider
              -> ZipArchiveProvider
              -> future Remote providers
```

The UI renders `YFileNode` and `YFileRef` only. It does not contain provider-specific pages.

## Provider contract

Each provider declares its capabilities and owns its path semantics, listing, metadata, creation,
rename/delete, direct copy/move where supported, and chunked read/write.

A `YFileRef` is namespaced by `providerId`. A local filesystem path and an SAF URI are therefore
never treated as the same address type.

## Cross-provider transfer

Same-provider copy/move first uses the provider-native operation.

When provider-native transfer is unavailable or providers differ, `DefaultYFilesEngine` performs a
chunked stream copy through `read()` and `write()`. Directory trees are recreated recursively.
Move uses copy followed by source deletion and removes the destination again if source deletion
fails.

Conflict handling supports rename, replace and skip.

## Providers

### Local

Provides normal storage operations, recursive search, random-access read/write, chmod and symbolic
links within its configured root boundary.

### Document / SAF

Uses Android `DocumentsContract`, persisted tree grants, URI-based references and document-provider
native copy/move where available.

### Root

Uses the shared YSuite `RootGateway`. Root shell execution lives in `core:platform:android`, not in
the feature. The provider exposes filesystem browsing, mutation, random-access transfer, chmod and
symbolic links.

### ZIP Archive

ZIP files mount as a read-only virtual filesystem and use the same YFiles browser. ZIPs originating
from SAF/Root are streamed into temporary cache files before mounting.

## Services

YFiles includes provider-neutral services for favorites/recent locations, recycle-bin records,
archives, hashes, duplicate detection, directory analysis, text and HEX previews, bulk rename,
split/join, comparison and cleanup analysis.

## Remote extension

`YFileProviderKind.Remote` is reserved for SMB/SFTP/WebDAV. Adding one requires only a provider and
credential/configuration layer; the existing browser, selection model, copy/move engine and tools do
not need a separate UI.
