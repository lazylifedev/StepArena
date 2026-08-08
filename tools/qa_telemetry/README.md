# QA telemetry export

This development-PC path reads only `steparena-dev` through the existing Google ADC and writes sanitized QA diagnostics to the private `lazylifedev/StepArena-QA-Telemetry` repository. It never creates or reads a Production Firebase client.

```powershell
.\tools\qa_telemetry\export_qa_telemetry.ps1 `
  -TelemetryRepoPath 'D:\private\StepArena-QA-Telemetry' `
  -SinceHours 168 -CommitAndPush
```

The exporter:

- exports only `POCO_X7_PRO_QA` and `SOV41_QA`;
- refuses a Firebase project other than `steparena-dev`;
- refuses a GitHub remote other than `lazylifedev/StepArena-QA-Telemetry` and verifies API visibility is `private` before reading telemetry;
- sanitizes raw Firestore documents before they reach the repository;
- uses a named Windows mutex and exits cleanly when another run is active;
- stages explicit generated file paths only, and makes no commit/push when the content is unchanged.

Install the user-session Scheduled Task after cloning the private repository:

```powershell
.\tools\qa_telemetry\install_scheduled_task.ps1 `
  -TelemetryRepoPath 'D:\private\StepArena-QA-Telemetry'
```

The task runs as the current `gasir` user, at logon and once per hour, with interactive credentials only. It does not store a password, ADC file, GitHub token, service-account key, App Check token, refresh token, UID, email, challenge ID, Google account, Wi-Fi identifier, or precise location.
