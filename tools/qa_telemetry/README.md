# QA telemetry export

This is a development-PC export path for the QA project only. It reads `steparena-dev` through Firebase Admin credentials already provided by the machine (ADC or an externally managed credential provider); no credential file belongs in this repository.

```powershell
.\tools\qa_telemetry\export_qa_telemetry.ps1 -TelemetryRepoPath 'D:\private\StepArena-QA-Telemetry'
```

The script refuses a project other than `steparena-dev`, writes only sanitized reports, and does not commit or push unless `-CommitAndPush` is explicitly supplied. If used with a Scheduled Task, run it at a one-hour interval with “run as soon as possible after a scheduled start is missed”.

Before enabling the task, verify the destination is a private GitHub repository and that the machine has read access to Firebase QA and push access to that repository. Never put a service-account key, Firebase token, UID, email, or App Check token in this repository.
