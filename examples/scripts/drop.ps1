# put stores bytes and prints a CID. get dials that peer and prints the same bytes.
. "$PSScriptRoot\lib.ps1"
try {
    Ensure-Compiled
    Write-Output "RUN drop"
    $payload = Join-Path $Work "drop.txt"
    Write-Bytes $payload "drop-bytes"
    $put = Start-Node "drop-put" "examples.Drop" ("put " + (ConvertTo-JavaPath $payload))
    Wait-Out $put "CID: " 90 | Out-Null
    $listen = Get-Field $put "Listening"
    $cid = Get-Field $put "CID"
    $get = Start-Node "drop-get" "examples.Drop" "get $listen $cid"
    Wait-Out $get "drop-bytes" 40 | Out-Null
    Write-Output "PASS drop"
    exit 0
} catch {
    Write-Output "FAIL drop"
    Write-Output $_
    exit 1
} finally {
    Stop-Nodes
}
