# Phase 7.5.2 Motion Integrity

## Boundary

Android's Step Counter remains the source of total activity. Motion evidence only partitions a new
Counter delta into competitive `eligible`, `restricted`, and `excluded` steps. Raw motion samples
are held in memory, are bounded, and are neither persisted nor transmitted. Missing or incomplete
evidence is `UNKNOWN` and cannot restrict steps. Existing version 2 integrity rows are not changed.

## Capture

- `TYPE_STEP_COUNTER` and `TYPE_STEP_DETECTOR` remain normally registered.
- A Detector starts or extends a short motion window.
- `TYPE_GYROSCOPE` and `TYPE_LINEAR_ACCELERATION` are sampled at a requested 50 ms period.
- If linear acceleration is unavailable, `TYPE_ACCELEROMETER` is converted with a low-pass gravity
  estimate. If gyroscope or acceleration is unavailable, confirmation is impossible.
- Capture ends four seconds after the latest Detector; stop and destroy cancel it immediately.
- Each sensor ring buffer is capped at 512 samples. The serialized step channel is capped at 1,024.

## Conservative decision boundary

`SHAKE_CONFIRMED` requires all of: at least 2,000 ms, at least 70% gyroscope and acceleration
coverage relative to 20 Hz, gyroscope RMS at least 5 rad/s, linear-acceleration RMS at least 4 m/s²,
angular reversal rate at least 5/s, dominant frequency at least 3.2 Hz, and periodicity at least 0.55.
High rotation without the complete evidence set is at most `SHAKE_SUSPECTED`. Sparse delivery is
`UNKNOWN`. A completed window must cover multiple Detector events before its confirmed evidence can
exclude multiple competitive steps; each Detector is consumed by at most one Counter delta.

## Allocation

Classifier version 3 caps confirmed and suspected Detector counts to the Counter delta. Confirmed
counts are excluded first, suspected counts are restricted from the remainder, and all unknown or
uncovered steps remain subject only to the existing cadence and burst classifier. Allocation uses a
single result satisfying `total = eligible + restricted + excluded`, with no negative values or
double subtraction. Home, notification, hourly, daily, and session totals continue to use the full
Counter delta; challenge and league calculations continue to use eligible steps.

## Limits

Motion evidence cannot prove intent or perfectly distinguish every device movement. The UI therefore
uses neutral language and does not label users as cheating. Production release contains no QA fixture
implementation; deterministic synthetic fixtures live only in the QA source set.
