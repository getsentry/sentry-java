# Explicit multi-stack observation API

Status: done
Type: AFK
Blocked by: 01

## Parent

.scratch/nav3-multiple-backstacks/PRD.md

## What to build

Add a public Navigation 3 API for app-owned multiple back stacks. The API should accept the selected stack, retained stack snapshots, stacks-in-use, stack name extraction, destination name extraction, and optional argument extraction. It should write full retained-stack context, mark selected and in-use stacks, and treat selected-stack changes as navigation events.

## Acceptance criteria

- [x] Public API supports a selected stack key and a map of stack keys to stack snapshots.
- [x] Public API supports `stacks_in_use` so retained background stacks are distinguishable from displayed stacks.
- [x] `stackNameExtractor` customizes stack names, with a readable default when omitted.
- [x] Selected-stack changes emit breadcrumbs, update `scope.screen`, and start navigation transactions when enabled.
- [x] Inactive retained stack changes refresh crash context without becoming the primary route unless selected or visible.
- [x] Unit and Compose tests cover API wiring, selected-stack switches, and inactive stack updates.

## Blocked by

- 01

## Comments
