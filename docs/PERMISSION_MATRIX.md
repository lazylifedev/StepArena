# Permission matrix

Phase 2 uses only the device step counter. Location, body-sensor, exact-alarm and
battery-optimization exemption permissions are not required.

| API | Activity recognition | Notifications | Health foreground service |
|---|---|---|---|
| 29-32 | Manifest + runtime request | No runtime permission; channel/user setting applies | `FOREGROUND_SERVICE`; health type declared |
| 33 | Manifest + runtime request | `POST_NOTIFICATIONS` runtime request | Same as above |
| 34-36 | Manifest + runtime request | `POST_NOTIFICATIONS` runtime request | `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_HEALTH`; health type declared |

Normal service starts are user initiated. The battery settings screen is opened
only after an explicit tap; StepArena does not request
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
