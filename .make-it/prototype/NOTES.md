# Prototype Notes

Question: does the proposed context model represent single-stack, multi-stack, and multipane-visible entries without confusing selected stack state with rendered UI state?

Run command:

```bash
javac .make-it/prototype/Nav3MultiStackPrototype.java && java -cp .make-it/prototype Nav3MultiStackPrototype
```

Initial answer:

- `selected_stack` and `stacks_in_use` need to be separate fields. The nav3-recipes responsive navigation pattern can render entries from both the start stack and selected stack.
- `visible_entries` should include `stack` when stack ownership is known. Otherwise a flattened `NavDisplay` loses which retained stack produced each visible route.
- `backstacks` should remain the durable retained state. `visible_entries` should remain current rendered UI state.
- A default primary route policy that prefers a visible entry from `selected_stack` is reasonable, but custom multipane scenes still need `primaryRouteSelector`.
