# Activity metrics

## Definitions

- Steps are the non-negative delta of Android `TYPE_STEP_COUNTER`. Detector events help
  place those steps in time but are never an additional step source.
- Distance is `steps × step length`. Step length is selected from manual setting,
  `height × 0.415`, then the declared 0.70 m application default.
- Walking duration is derived from observed activity intervals, not foreground-service
  uptime. Unknown duration remains absent.
- Estimated calories use `distance(km) × weight(kg) × 0.5`. A missing weight uses the
  declared 60 kg default. Results are capped at 10,000 kcal.
- Average moving speed is `distance(km) ÷ active duration(hours)`. It is absent below
  60 seconds, when distance/duration is unavailable, or outside 0–25 km/h.

Distance and calories in Phase 3 are estimates, not measurements. These values are for
general wellness feedback and are not medical measurements.

## Quality and missing data

Each stored metric is `MEASURED`, `ESTIMATED`, `RECOVERED`, `MIXED`, or `UNKNOWN`.
Counter steps can be measured; Phase 3 distance and calories are always estimated.
Long or restart-related counter gaps are recovered. Mixed allocation is used when
counter totals and detector timestamps both contribute. Unknown values appear as `―`,
never as a fabricated zero.

Detector timestamps allocate known steps to their hours. A remainder is assigned inside
the counter interval and marked estimated. Without detector data, an interval crossing
an hour boundary is divided by elapsed-time ratio while preserving the counter total.
An interval of two hours or more is classified as recovered. The daily invariant is:

`daily steps = assigned hourly steps + unclassified steps`
# Phase 3.1 corrections

Walking duration is cumulative active time. With detector events, only consecutive
event gaps of 60 seconds or less are added. Without detector evidence, a counter
gap of 60 seconds or less is added as `ESTIMATED`; longer gaps add no duration.
Distance uses the step length stored on each hourly record. Calories retain the
current `distanceKm * weightKg * 0.5` estimate; missing weight uses 60 kg and the
UI discloses that fallback.
