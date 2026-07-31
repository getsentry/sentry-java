# Argument, safety, and edge-case coverage

Status: done
Type: AFK
Blocked by: 02, 03

## Parent

.scratch/nav3-multiple-backstacks/PRD.md

## What to build

Extend the integration's defensive behavior and edge-case coverage to the new multi-stack API. This includes extractor safety for stack names and primary route selection, privacy-preserving argument behavior, duplicate or equal route keys across stacks, per-stack max backstack capping, and disabled option behavior.

## Acceptance criteria

- [x] Throwing `stackNameExtractor` and `primaryRouteSelector` callbacks do not crash the host app.
- [x] Throwing key equality, hashing, or string conversion remains guarded in multi-stack paths.
- [x] Route arguments remain absent unless an argument extractor is provided.
- [x] Extracted arguments are attached consistently to breadcrumbs, transactions, backstack entries, and visible entries when enabled.
- [x] Max backstack size is applied per retained stack.
- [x] Equal route keys in different stacks do not break stack ownership or visible-entry reporting.
- [x] Disabled breadcrumb, tracing, screen tracking, and backstack context options still behave correctly.

## Blocked by

- 02
- 03

## Comments
