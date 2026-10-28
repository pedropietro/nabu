# get starts first. put announces the block to it. get fetches using only the CID.
. "$PSScriptRoot\lib.ps1"
try {
    Ensure-Compiled
    Write-Output "RUN find"
    $payload = Join-Path $Work "find.txt"
    Write-Bytes $payload "find-bytes"
    $get = Start-Node "find-get" "examples.Find" "get"
    Wait-Out $get "Listening: " 90 | Out-Null
    $listen = Get-Field $get "Listening"
    $put = Start-Node "find-put" "examples.Find" ("put --bootstrap $listen " + (ConvertTo-JavaPath $payload))
    Wait-Out $put "CID: " 120 | Out-Null
    $cid = Get-Field $put "CID"
    Send-Line $get $cid
    Wait-Out $get "Providers:" 90 | Out-Null
    Wait-Out $get "find-bytes" 45 | Out-Null
    Write-Output "PASS find"
    exit 0
} catch {
    Write-Output "FAIL find"
    Write-Output $_
    exit 1
} finally {
    Stop-Nodes
}
