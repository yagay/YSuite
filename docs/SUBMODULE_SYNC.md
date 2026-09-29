# Feature submodule sync

YSuite tracks each feature repository as a Git submodule on its `main` branch. After changing a feature repository, update the corresponding gitlink in YSuite before treating the unified build as verified.

The feature source itself remains authoritative; YSuite should never copy feature source into the host.
