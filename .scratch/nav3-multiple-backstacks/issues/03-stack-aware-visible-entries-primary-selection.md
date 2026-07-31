# Stack-aware visible entries and primary selection

Status: ready-for-agent
Type: AFK
Blocked by: 02

## Parent

.scratch/nav3-multiple-backstacks/PRD.md

## What to build

Make visible-entry tracking stack-aware for multiple-backstack apps and add an overrideable primary route selection hook. A completed slice lets Sentry report rendered entries from multiple stacks, choose the correct primary route for screen and transaction data, and preserve existing multipane behavior for custom and Material scene patterns.

## Acceptance criteria

- [ ] `visible_entries` includes `stack` when stack ownership is known.
- [ ] Visible entries remain current rendered UI state, separate from retained `backstacks`.
- [ ] A `primaryRouteSelector` callback can choose the primary visible route.
- [ ] Default primary route selection prefers a visible entry from the selected stack and falls back conservatively when metadata is inconclusive.
- [ ] `scope.screen`, transaction names, breadcrumbs, and `contexts.app.view_names` use the selected primary route and visible entries correctly.
- [ ] Tests cover visible entries from multiple stacks and primary route override behavior.

## Blocked by

- 02

## Comments
