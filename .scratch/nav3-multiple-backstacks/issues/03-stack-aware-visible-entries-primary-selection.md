# Stack-aware visible entries and primary selection

Status: done
Type: AFK
Blocked by: 02

## Parent

.scratch/nav3-multiple-backstacks/PRD.md

## What to build

Make visible-entry tracking stack-aware for multiple-backstack apps and add an overrideable primary route selection hook. A completed slice lets Sentry report rendered entries from multiple stacks, choose the correct primary route for screen and transaction data, and preserve existing multipane behavior for custom and Material scene patterns.

## Acceptance criteria

- [x] `visible_entries` includes `stack` when stack ownership is known.
- [x] Visible entries remain current rendered UI state, separate from retained `backstacks`.
- [x] A `primaryRouteSelector` callback can choose the primary visible route.
- [x] Default primary route selection prefers a visible entry from the selected stack and falls back conservatively when metadata is inconclusive.
- [x] `scope.screen`, transaction names, breadcrumbs, and `contexts.app.view_names` use the selected primary route and visible entries correctly.
- [x] Tests cover visible entries from multiple stacks and primary route override behavior.

## Blocked by

- 02

## Comments
