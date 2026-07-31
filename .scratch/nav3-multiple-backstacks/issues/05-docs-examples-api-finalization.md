# Docs, examples, and API finalization

Status: done
Type: AFK
Blocked by: 01, 02, 03, 04

## Parent

.scratch/nav3-multiple-backstacks/PRD.md

## What to build

Finalize the Navigation 3 multiple-backstack feature for review by updating docs, examples, generated API metadata, formatting, and verification. A completed slice should leave users with clear single-stack and multiple-stack guidance and leave the module passing the relevant checks.

## Acceptance criteria

- [x] README and examples describe the unified context model and the explicit multiple-stack wiring.
- [x] Examples cover selected stack, retained stack snapshots, stacks-in-use, stack name extraction, and primary route selection.
- [x] Privacy notes still explain that extracted arguments are sent as-is and are not gated by `sendDefaultPii`.
- [x] Formatting is applied.
- [x] API dump is regenerated.
- [x] Relevant Navigation 3 module tests pass.

## Blocked by

- 01
- 02
- 03
- 04

## Comments
