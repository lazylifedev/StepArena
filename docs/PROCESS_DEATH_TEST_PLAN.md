# Process death test plan

Keep these cases separate:

1. Activity finish: Back/home only; the foreground service should continue.
2. Recents removal: swipe the task card; record the SOV41 result without
   generalizing it to all Android devices.
3. Process termination: use a non-force-stop mechanism. On this Android 11
   device `am kill` did not kill the foreground-service process. `am crash`
   terminated the process without placing the package in stopped state and
   reproduced START_STICKY recreation.
4. Android Settings force-stop: this places the app in stopped state. Automatic
   recovery is not expected and must not be claimed.

For process recreation record PID, session, steps, last sensor value, baseline,
service record, sensor registration, notification, and ApplicationExitInfo
before and after. Pass requires one new service/session, restored
`trackingRequested`, no step rollback/double addition, foreground notification
restoration, and sensor re-registration.

For force-stop, confirm the process/service/notification disappear and remain
absent. On manual launch, reconcile the persisted state to stopped and expose
the previous exit reason. A description matching `stop <package>...` is treated
as likely Settings force-stop; `kill background` is not.

## Evidence wording

`am crash` evidence must be described as: crash-triggered process recreation,
PID change, `START_STICKY` Service recreation, DataStore restoration,
notification recreation, and sensor re-registration. It must not be described
as a complete reproduction of low-memory or ordinary OS process termination.

Task swipe/activity finish, Settings force-stop, `am crash`, and ordinary OS
termination remain separate cases. Exit records are keyed by timestamp, reason,
and process so an already processed record is not presented as a new exit.
Unknown reasons, null descriptions, and unavailable PSS/RSS are non-fatal.
`USER_REQUESTED` descriptions caused by package replacement
(`installPackageLI`) are not Settings force-stop and must not clear requested
tracking state.

After sticky recreation, test with the saved last cumulative value and then a
higher value. Only the difference may be added. A stop PendingIntent carrying
an older session ID must be rejected after a new session is established.
