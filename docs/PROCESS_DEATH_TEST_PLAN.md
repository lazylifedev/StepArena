# Process death test plan

Normal process death and Android Settings **Force stop** are different cases.
`START_STICKY` may recreate a normally killed service, but is not a recovery
guarantee. Force-stopped apps cannot restart themselves until the user launches
them again.

## Normal recreation

1. Start tracking from the app and establish a sensor baseline.
2. Walk and record the app count, sensor value, heartbeat, and service session.
3. Remove the activity from Recents; do not force stop the package.
4. Walk with the display off, wait, then reopen the app.
5. Confirm the persisted last sensor value is used and only the new delta is added.
6. If the OS kills the process, confirm a sticky recreation restores
   `trackingRequested`, creates/retains an appropriate service session, and does
   not add the boot cumulative counter as today's steps.

## Force stop

Force stop in Android Settings. Confirm the service and notification remain
stopped. Reopen StepArena and show a stopped/stale diagnostic; never claim that
automatic recovery was expected.
