param(
    [string]$TelemetryRepoPath = 'D:\private\StepArena-QA-Telemetry',
    [string]$TaskName = 'StepArena QA Telemetry Export'
)

$ErrorActionPreference = 'Stop'
$scriptPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'export_qa_telemetry.ps1')).Path
$repoPath = (Resolve-Path -LiteralPath $TelemetryRepoPath).Path
$identity = "$env:USERDOMAIN\$env:USERNAME"
$argument = "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$scriptPath`" -TelemetryRepoPath `"$repoPath`" -SinceHours 168 -CommitAndPush"
$action = New-ScheduledTaskAction -Execute 'powershell.exe' -Argument $argument
$hourly = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Hours 1) -RepetitionDuration (New-TimeSpan -Days 3650)
$atLogOn = New-ScheduledTaskTrigger -AtLogOn -User $identity
$principal = New-ScheduledTaskPrincipal -UserId $identity -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 30) -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries
$task = New-ScheduledTask -Action $action -Trigger @($hourly, $atLogOn) -Principal $principal -Settings $settings -Description 'Export sanitized QA telemetry from steparena-dev to the private StepArena-QA-Telemetry repository.'
Register-ScheduledTask -TaskName $TaskName -InputObject $task -Force | Out-Null
Get-ScheduledTask -TaskName $TaskName | Select-Object TaskName, State, TaskPath
