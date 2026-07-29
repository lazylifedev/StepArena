# Walking session rules

A walking session is a view of steps already recorded in hourly and daily activity; it
does not generate or add steps. The default active gap is 60 seconds and the automatic
end gap is five minutes. Noise sessions must reach 10 steps or one minute before they
are retained as completed activity.

The foreground-service session ID is the idempotency key for manual sessions. Session
rows contain active, elapsed and paused duration, distance, calorie and moving/elapsed
speed estimates, source quality, zone, and original timestamps. Future ranked-match
metadata can reference the session without changing the activity source.
