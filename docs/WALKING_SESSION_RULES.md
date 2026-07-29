# Walking session rules

A walking session is a view of steps already recorded in hourly and daily activity; it
does not generate or add steps. The default active gap is 60 seconds and the automatic
end gap is five minutes. Noise sessions must reach 10 steps or one minute before they
are retained as completed activity.

The foreground-service session ID is the idempotency key for manual sessions. Session
rows contain active, elapsed and paused duration, distance, calorie and moving/elapsed
speed estimates, source quality, zone, and original timestamps. Future ranked-match
metadata can reference the session without changing the activity source.
# Phase 3.1 session boundaries

Tracking-service sessions and walking sessions are independent. Automatic and
manual walking sessions have their own UUID and retain the source service ID only

## Phase 3.2 manual walk lifecycle

The foreground tracking service and a manual walk are independent states. Starting a
manual walk starts the service only when necessary, then creates one `MANUAL_WALK /
ACTIVE` UUID after tracking is available. When tracking already runs, its service UUID
and sensor registrations are retained. Ending the manual UUID completes only that
session; tracking and automatic detection remain enabled.

A manual session is the authoritative session while active, so a counter delta is
recorded once in hourly/daily aggregates and once in the manual session, never also in
an automatic session. An active automatic session is closed before manual start.
Automatic detection may begin again on a later step after manual completion.

At local midnight the old manual session completes at the boundary and a new manual
UUID starts for the new date. A session never spans dates. Service recreation restores
the active manual row from Room and does not create a duplicate. Android force-stop
does not automatically restart tracking; a later app launch must resolve any abnormal
ACTIVE row.

The notification action carries the manual UUID. A stale UUID cannot end a newer walk.
Stopping tracking is a separate action which finalizes every ACTIVE/PAUSED session
before unregistering sensors and removing the notification.
as a reference. Automatic sessions pause after 60 seconds without walking and
finish after five minutes. Sessions shorter than both 10 steps and 60 active
seconds are discarded. At finish, elapsed time is recomputed and paused time is
`max(0, elapsed - active)`. Manual sessions are not subject to the automatic
five-minute timeout.
