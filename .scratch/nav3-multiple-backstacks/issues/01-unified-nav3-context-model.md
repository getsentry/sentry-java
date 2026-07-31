# Unified Nav3 context model

Status: done
Type: AFK

## Parent

.scratch/nav3-multiple-backstacks/PRD.md

## What to build

Migrate the existing single-stack and multipane Navigation 3 integration behavior to the unified plural context model before the module is released. A completed slice keeps current breadcrumb, screen, transaction, and multipane behavior working, but changes navigation crash context to use `selected_stack`, `stacks_in_use`, `backstacks`, and `visible_entries` with a `default` stack for single-stack apps.

## Acceptance criteria

- [x] Single-stack navigation context uses `selected_stack: default`, `stacks_in_use: [default]`, and one `backstacks` entry.
- [x] Existing route entries still include route names and opt-in arguments.
- [x] Existing multipane visible-route context is renamed to `visible_entries` and remains separate from retained backstack state.
- [x] `contexts.app.view_names`, `scope.screen`, breadcrumbs, and transactions still behave as before for single-stack and multipane cases.
- [x] Existing context tests are updated to assert the new plural shape.

## Blocked by

None - can start immediately

## Comments
