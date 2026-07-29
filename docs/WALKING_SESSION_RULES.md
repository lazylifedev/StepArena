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
as a reference. Automatic sessions pause after 60 seconds without walking and
finish after five minutes. Sessions shorter than both 10 steps and 60 active
seconds are discarded. At finish, elapsed time is recomputed and paused time is
`max(0, elapsed - active)`. Manual sessions are not subject to the automatic
five-minute timeout.
