# Run every example test. Skip the daemon test with -SkipApi.
param([switch]$SkipApi)

$ErrorActionPreference = "Stop"
$scripts = @("chat.ps1", "drop.ps1", "find.ps1", "name.ps1")
if (-not $SkipApi) { $scripts += "api.ps1" }

foreach ($script in $scripts) {
    & (Join-Path $PSScriptRoot $script)
    if ($LASTEXITCODE -ne 0) {
        Write-Output "retry $script"
        & (Join-Path $PSScriptRoot $script)
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }
}
Write-Output "PASS all"
exit 0
