# Sentry Travel UI Prototype Notes

## Visual Direction

Use a polished travel-planning surface, not a debug grid.

- Palette: midnight navy background, Sentry purple primary, coral/orange accent, cream cards.
- Layout: hero card, rounded destination cards, itinerary timeline rows, compact metric chips.
- Tone: “Plan your next traceable trip” as a playful internal sample tagline.
- Placeholder imagery: gradient blocks and emoji/icons instead of new assets.

## Navigation Prototype

Primary navigation graph:

```text
home
  -> explore
    -> destination/{id}
      -> stay/{id}
        -> review
          -> confirmation
  -> my-trips
    -> trip-detail/{id}
      -> itinerary-day/{day}
        -> activity-detail/{id}
  -> profile
  -> support
```

This graph gives Buddy a deep flow with a minimum path of seven screens:

`Home -> Explore -> Destination -> Stay -> Review -> Confirmation -> My Trips`.

## Screen Sketches

### Home

- Large “Sentry Travel” hero with trip summary chips.
- Primary buttons: `Explore destinations`, `My trips`, `Traveler profile`, `Support`.
- Featured destinations carousel/list.
- Span actions: `Refresh deals` (HTTP), `Score picks` (custom span).

### Explore

- Search field and travel style filters.
- Destination cards with route to details.
- Span actions: search submitted breadcrumb, recommendation scoring custom span.

### Destination Details

- Destination hero, highlights, recommended stay cards.
- Primary action: `Check availability` (HTTP span via GitHub API wrapper).
- Secondary action: `Build itinerary` (custom span).

### Stay Details

- Stay summary, amenities, price breakdown.
- Primary action: `Reserve this stay`.
- Custom span: price calculation.

### Booking Review

- Traveler summary, stay details, day plan, total.
- Primary action: `Confirm trip`.
- DB span: save booking.

### Confirmation

- Success state, confirmation number, next steps.
- Actions: `View saved trip`, `Plan another`.

### My Trips / Trip Details / Itinerary

- DB span: load saved trips.
- Timeline rows for itinerary days and activities.
- Activity detail screens for deep navigation.

### Profile

- Traveler preferences, toggles, home airport.
- DB span: save preferences.

### Support

- Contact support button triggers HTTP span.
- Explicit `Simulate booking failure` captures an exception but keeps UI usable.

## Validated Decisions

- Keep implementation self-contained in one activity file unless size becomes unmanageable.
- Use existing sample app dependencies only.
- Reuse `GithubAPI.service.listReposAsync(...)` for safe HTTP spans.
- Add a tiny direct SQLite helper inside the travel activity for DB spans.
- Use `Sentry.addBreadcrumb`, `Sentry.captureException`, and manual child spans for action context.
