# publish two lines under one PeerId. resolve follows the name, then fetches the first CID again.
. "$PSScriptRoot\lib.ps1"
try {
    Ensure-Compiled
    Write-Output "RUN name"
    $resolve = Start-Node "name-resolve" "examples.Name" "resolve"
    Wait-Out $resolve "Listening: " 90 | Out-Null
    $listen = Get-Field $resolve "Listening"
    $publish = Start-Node "name-publish" "examples.Name" "publish --bootstrap $listen"
    Wait-Out $publish "This PeerId is the IPNS name" 90 | Out-Null
    $peer = Get-Field $publish "PeerId"
    Send-Line $resolve $peer
    Send-Line $publish "alpha"
    Wait-Out $publish "IPNS record stored" 90 | Out-Null
    $first = Get-Field $publish "CID"
    Send-Line $resolve "resolve"
    Wait-Out $resolve "alpha" 60 | Out-Null
    Send-Line $publish "beta"
    Wait-Count $publish "IPNS record stored" 2 90 | Out-Null
    $second = Get-Field $publish "CID"
    if ($first -eq $second) { throw "second CID matched the first" }
    Send-Line $resolve "resolve"
    Wait-Out $resolve "beta" 60 | Out-Null
    Send-Line $resolve "get $first"
    Wait-Count $resolve "alpha" 2 60 | Out-Null
    Write-Output "PASS name"
    exit 0
} catch {
    Write-Output "FAIL name"
    Write-Output $_
    exit 1
} finally {
    Stop-Nodes
}
