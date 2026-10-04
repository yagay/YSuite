# Feature lifecycle

Feature UI registrations may expose a framework lifecycle observer.

Supported events:
- Activated
- Deactivated

The shared feature host owns dispatch. Features do not watch Activity lifecycle directly for simple
page activation/deactivation behavior.

The default observer is a no-op, so most features need no lifecycle implementation.
