# Device test results

## Phase 2 target

- Device: SOV41
- OS: Android 11
- Serial: `QV7209CF25`
- Compose instrumentation: 6/6 passed on 2026-07-29
- Sensor walking/service persistence scenarios: pending manual execution

Required evidence: activity-recognition grant/deny/settings recovery, 20-100
post-baseline steps, notification updates, screen-off walking, activity/Recents
removal, process recreation without double addition, explicit stop/restart, and
battery settings round trip. An existing pedometer's exact total is not a pass
condition; only post-baseline deltas and recovery are compared.

The automated device run covered home rendering/navigation, tracking start UI,
large-font reachability, stopped-state presentation, and onboarding rationale.
It did not physically walk the device, turn the screen off, swipe the task, or
validate a sticky process recreation; those results must not be inferred from
the Compose run.
