param(
    [Parameter(Mandatory = $true)][string]$TelemetryRepoPath,
    [ValidateRange(1, 168)][int]$SinceHours = 168,
    [switch]$CommitAndPush
)

$ErrorActionPreference = 'Stop'
$mutex = $null

function Resolve-GhPath {
    $command = Get-Command gh.exe -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $defaultPath = 'C:\Program Files\GitHub CLI\gh.exe'
    if (Test-Path -LiteralPath $defaultPath) { return $defaultPath }
    return $null
}

function Assert-PrivateTelemetryRemote([string]$repo) {
    $gh = Resolve-GhPath
    if (-not $gh) { throw 'GitHub CLI is required for the private-repository guard' }
    $remote = (& git -C $repo remote get-url origin).Trim()
    if ($LASTEXITCODE -ne 0 -or $remote -notmatch 'github\.com[:/](?<fullName>[^/]+/[^/]+?)(?:\.git)?$') {
        throw 'Telemetry repository origin must be a GitHub repository'
    }
    $fullName = $Matches.fullName
    if ($fullName -ne 'lazylifedev/StepArena-QA-Telemetry') {
        throw "Unexpected telemetry repository: $fullName"
    }
    $visibility = (& $gh api "repos/$fullName" --jq '.visibility').Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Unable to verify GitHub repository visibility' }
    if ($visibility -ne 'private') { throw "Refusing telemetry export: GitHub visibility is $visibility" }
}

function Get-ExportFiles([string]$repo) {
    $paths = @('README.md')
    foreach ($root in @('latest', 'daily')) {
        $rootPath = Join-Path $repo $root
        if (Test-Path -LiteralPath $rootPath) {
            $paths += Get-ChildItem -LiteralPath $rootPath -Recurse -File | ForEach-Object {
                $_.FullName.Substring($repo.Length).TrimStart('\')
            }
        }
    }
    return @($paths | Sort-Object -Unique)
}

try {
    $mutex = [System.Threading.Mutex]::new($false, 'Global\StepArena_QA_Telemetry_Export')
    if (-not $mutex.WaitOne(0)) {
        Write-Output 'Exporter already running; exiting without duplicate work.'
        exit 0
    }

    $repo = (Resolve-Path -LiteralPath $TelemetryRepoPath).Path
    $workspace = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
    $functionsDir = Join-Path $workspace 'functions'
    $exporter = Join-Path $PSScriptRoot 'export_firestore.cjs'
    $reportBuilder = Join-Path $PSScriptRoot 'build_report.py'
    $latestJson = Join-Path $repo 'latest\latest.json'

    if (-not (Test-Path -LiteralPath (Join-Path $repo '.git'))) { throw "TelemetryRepoPath is not a Git repository: $repo" }
    Assert-PrivateTelemetryRemote $repo
    if (-not (Get-Command node -ErrorAction SilentlyContinue)) { throw 'node is required' }
    if (-not (Get-Command python -ErrorAction SilentlyContinue)) { throw 'python is required' }

    $env:GCLOUD_PROJECT = 'steparena-dev'
    Push-Location $functionsDir
    try {
        & node $exporter --output $repo --since-hours $SinceHours
        if ($LASTEXITCODE -ne 0) { throw 'Firestore export failed' }
    } finally { Pop-Location }
    & python $reportBuilder --input $latestJson --output $repo
    if ($LASTEXITCODE -ne 0) { throw 'Report build failed' }

    if ($CommitAndPush) {
        $branch = (& git -C $repo branch --show-current).Trim()
        if ([string]::IsNullOrWhiteSpace($branch)) { throw 'Telemetry repository is in detached HEAD state' }
        $files = Get-ExportFiles $repo
        if ($files.Count -eq 0) { throw 'No sanitized export files were generated' }
        & git -C $repo add -- $files
        if ($LASTEXITCODE -ne 0) { throw 'git add failed' }
        & git -C $repo diff --cached --quiet
        if ($LASTEXITCODE -eq 0) {
            Write-Output 'No telemetry change; no commit or push required.'
        } elseif ($LASTEXITCODE -eq 1) {
            & git -C $repo commit -m 'Update sanitized QA telemetry report'
            if ($LASTEXITCODE -ne 0) { throw 'git commit failed' }
            & git -C $repo push origin $branch
            if ($LASTEXITCODE -ne 0) { throw 'git push failed' }
        } else { throw 'Unable to inspect staged telemetry changes' }
    }
} catch {
    Write-Error "QA telemetry exporter stopped safely: $($_.Exception.Message)"
    exit 1
} finally {
    if ($mutex) {
        try { $mutex.ReleaseMutex() | Out-Null } catch { }
        $mutex.Dispose()
    }
}
