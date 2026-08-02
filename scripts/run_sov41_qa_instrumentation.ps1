[CmdletBinding()]
param(
    [string]$Serial = "QV7209CF25",
    [string]$QaApk = "$PSScriptRoot\..\app\build\outputs\apk\qa\debug\app-qa-debug.apk",
    [string]$TestApk = "$PSScriptRoot\..\app\build\outputs\apk\androidTest\qa\debug\app-qa-debug-androidTest.apk",
    [string]$EvidenceDirectory = "$PSScriptRoot\..\build\qa-instrumentation-evidence"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$ProductionPackage = "com.lazyapps.steparena"
$QaPackage = "com.lazyapps.steparena.qa"
$QaTestPackage = "com.lazyapps.steparena.qa.test"
$Runner = "$QaTestPackage/androidx.test.runner.AndroidJUnitRunner"
$PedometerPackage = "com.lazyapps.pedometer"

function Fail([string]$Message) { throw "SAFETY STOP: $Message" }
function Assert-SafeAdbArguments([object[]]$Arguments) {
    $command = ($Arguments | ForEach-Object { [string]$_ }) -join " "
    if ($command -match "(?:^|\s)uninstall\s+$([regex]::Escape($ProductionPackage))(?:\s|$)") {
        Fail "production uninstall is forbidden"
    }
    if ($command -match "shell\s+pm\s+clear\s+$([regex]::Escape($ProductionPackage))(?:\s|$)") {
        Fail "production pm clear is forbidden"
    }
    if ($command -match "shell\s+am\s+instrument" -and $command -notmatch [regex]::Escape($Runner)) {
        Fail "only the QA test runner may be instrumented"
    }
    if ($command -match "(?:^|\s)install(?:\s|$)") {
        $apk = [string]$Arguments[-1]
        $allowed = @($QaApk, $TestApk) | ForEach-Object { [IO.Path]::GetFullPath($_) }
        if ([IO.Path]::GetFullPath($apk) -notin $allowed) { Fail "only QA APKs may be installed" }
    }
}
function Invoke-Adb {
    Assert-SafeAdbArguments $args
    $output = & $script:Adb @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($args -join ' ')`n$($output -join "`n")" }
    return $output
}
function Get-PackageUid([string]$Package) {
    $line = (Invoke-Adb -s $Serial shell pm list packages -U $Package | Select-String "package:$([regex]::Escape($Package)) uid:").Line
    if (-not $line) { Fail "required package is missing: $Package" }
    return ([regex]::Match($line, "uid:(\d+)").Groups[1].Value)
}
function Get-ApkManifest([string]$Apk) {
    $text = & $script:Aapt dump xmltree $Apk AndroidManifest.xml 2>&1
    if ($LASTEXITCODE -ne 0) { throw "aapt manifest inspection failed for $Apk" }
    return ($text -join "`n")
}
function Assert-ApkIdentity([string]$Apk, [string]$ExpectedPackage, [string]$ExpectedTarget = "") {
    if (-not (Test-Path -LiteralPath $Apk -PathType Leaf)) { Fail "APK not found: $Apk" }
    $manifest = Get-ApkManifest $Apk
    $packagePattern = 'package="{0}"' -f [regex]::Escape($ExpectedPackage)
    if ($manifest -notmatch $packagePattern) {
        Fail "APK package is not $ExpectedPackage : $Apk"
    }
    $targetPattern = 'targetPackage.*="{0}"' -f [regex]::Escape($ExpectedTarget)
    if ($ExpectedTarget -and $manifest -notmatch $targetPattern) {
        Fail "test APK target is not $ExpectedTarget : $Apk"
    }
    $productionTargetPattern = 'targetPackage.*="{0}"' -f [regex]::Escape($ProductionPackage)
    if ($manifest -match $productionTargetPattern) {
        Fail "test APK targets production"
    }
}
function Get-AppSnapshot([string]$Package) {
    $uid = Get-PackageUid $Package
    $path = (Invoke-Adb -s $Serial shell pm path $Package) -join "`n"
    $installed = (Invoke-Adb -s $Serial shell dumpsys package $Package | Select-String "lastUpdateTime=" | Select-Object -First 1).Line.Trim()
    $process = (Invoke-Adb -s $Serial shell ps -A | Select-String ("\s{0}$" -f [regex]::Escape($Package)) | ForEach-Object Line) -join "`n"
    $services = (Invoke-Adb -s $Serial shell dumpsys activity services $Package | Select-String $Package | ForEach-Object Line) -join "`n"
    $files = @(Invoke-Adb -s $Serial shell run-as $Package find databases shared_prefs files no_backup -type f | Sort-Object)
    $hashes = @($files | ForEach-Object { (Invoke-Adb -s $Serial shell run-as $Package sha256sum $_) -join "`n" })
    [ordered]@{ package=$Package; uid=$uid; apkPath=$path; lastUpdateTime=$installed; files=$files; hashes=$hashes; process=$process; services=$services }
}
function Compare-Snapshot($Before, $After, [string]$Name) {
    # Running applications may legitimately update data while QA tests execute. Preserve the
    # full pre/post manifests as evidence, but gate only immutable package-install identity.
    foreach ($field in @("uid", "apkPath", "lastUpdateTime")) {
        $left = ($Before.$field | Out-String).Trim()
        $right = ($After.$field | Out-String).Trim()
        if ($left -ne $right) { throw "$Name changed: $field" }
    }
}

if ($Serial -ne "QV7209CF25") { Fail "serial must be QV7209CF25" }
$script:Adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $script:Adb) { $script:Adb = "D:\Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path -LiteralPath $script:Adb)) { Fail "adb not found" }
$script:Aapt = Get-ChildItem "D:\Android\Sdk\build-tools" -Recurse -Filter aapt.exe | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
if (-not $script:Aapt) { Fail "aapt not found" }

$deviceLines = Invoke-Adb devices
$online = @($deviceLines | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s+device(?:\s|$)" })
if ($online.Count -ne 1) { Fail "SOV41 is not the single matching online device" }
Get-PackageUid $ProductionPackage | Out-Null
Get-PackageUid $PedometerPackage | Out-Null
Assert-ApkIdentity $QaApk $QaPackage
Assert-ApkIdentity $TestApk $QaTestPackage $QaPackage

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$runDirectory = Join-Path $EvidenceDirectory $stamp
New-Item -ItemType Directory -Path $runDirectory -Force | Out-Null
$before = [ordered]@{ production = Get-AppSnapshot $ProductionPackage; pedometer = Get-AppSnapshot $PedometerPackage }
$before | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $runDirectory "before.json")

# Only the two identities validated above may be installed or invoked.
Invoke-Adb -s $Serial install -r -t $QaApk | Tee-Object -FilePath (Join-Path $runDirectory "qa-install.txt")
Invoke-Adb -s $Serial install -r -t $TestApk | Tee-Object -FilePath (Join-Path $runDirectory "qa-test-install.txt")
$instrumentation = Invoke-Adb -s $Serial shell am instrument -w $Runner
$instrumentation | Set-Content -Encoding UTF8 (Join-Path $runDirectory "instrumentation.txt")

$after = [ordered]@{ production = Get-AppSnapshot $ProductionPackage; pedometer = Get-AppSnapshot $PedometerPackage }
$after | ConvertTo-Json -Depth 8 | Set-Content -Encoding UTF8 (Join-Path $runDirectory "after.json")
Compare-Snapshot $before.production $after.production "production"
Compare-Snapshot $before.pedometer $after.pedometer "pedometer"
if (($instrumentation -join "`n") -notmatch "OK \(\d+ tests\)") { throw "Instrumentation did not report a successful test count" }

Write-Host "SAFE QA instrumentation passed. Evidence: $runDirectory"
