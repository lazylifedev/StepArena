param(
    [Parameter(Mandatory = $true)][string]$TelemetryRepoPath,
    [int]$SinceHours = 168,
    [switch]$CommitAndPush
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath $TelemetryRepoPath).Path
$workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$functionsDir = Join-Path $workspace 'functions'
$exporter = Join-Path $PSScriptRoot 'export_firestore.cjs'
$reportBuilder = Join-Path $PSScriptRoot 'build_report.py'
$env:GCLOUD_PROJECT = 'steparena-dev'
$latestJson = Join-Path $repo 'latest.json'

if (-not (Test-Path -LiteralPath (Join-Path $repo '.git'))) { throw "TelemetryRepoPath is not a Git repository: $repo" }
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'node is required' }
if (-not (Get-Command python -ErrorAction SilentlyContinue)) { throw 'python is required' }

Push-Location $functionsDir
try { node $exporter --output $repo } finally { Pop-Location }
python $reportBuilder --input $latestJson --output $repo

if ($CommitAndPush) {
    $branch = (& git -C $repo branch --show-current).Trim()
    if ([string]::IsNullOrWhiteSpace($branch)) { throw 'Telemetry repository is in detached HEAD state' }
    & git -C $repo add -- latest latest.json README.md
    if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
    $changed = & git -C $repo diff --cached --quiet
    if ($LASTEXITCODE -ne 0) {
        & git -C $repo commit -m 'Update sanitized QA telemetry report'
        if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
        & git -C $repo push origin $branch
        if ($LASTEXITCODE -ne 0) { throw 'git push failed' }
    }
}
