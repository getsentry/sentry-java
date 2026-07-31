# Add dialog-like and multipane demo

Status: done
Type: AFK
Blocked by: 02

## Parent

.scratch/nav3-sample-app/PRD.md

## What to build

Extend the Navigation Activity with two additional manual verification scenarios: a dialog-like route and a multipane/list-detail route. Prefer the simplest compiling Navigation 3 runtime pattern for the dialog-like scenario; the goal is to make transient destination behavior inspectable, not to build a comprehensive dialog framework.

For the multipane/list-detail scenario, use the holder plus decorator API so visible entries and primary-route selection can be exercised. The UI should make it clear which list/detail entries are visible and which route should be treated as primary.

## Acceptance criteria

- [ ] The sample includes a dialog-like navigation scenario that can be opened and dismissed manually.
- [ ] The dialog-like scenario is represented clearly enough to inspect generated navigation telemetry.
- [ ] The sample includes a multipane/list-detail scenario with multiple visible entries.
- [ ] The multipane scenario wires `rememberSentryNavStateHolder` and `rememberSentryNavEntryDecorator`.
- [ ] The multipane scenario selects a detail route as primary through metadata or `primaryRouteSelector`.
- [ ] The sample still compiles without adding unnecessary Nav3 dependencies.

## Blocked by

- 02

## Comments
