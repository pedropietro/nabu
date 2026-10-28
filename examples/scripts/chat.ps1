# Two local chats. One sends a line; the other prints it.
. "$PSScriptRoot\lib.ps1"
try {
    Ensure-Compiled
    Write-Output "RUN chat"
    $a = Start-Node "chat-a" "examples.Chat" ""
    Wait-Out $a "Listening: " 90 | Out-Null
    $listen = Get-Field $a "Listening"
    $b = Start-Node "chat-b" "examples.Chat" "--bootstrap $listen"
    Wait-Out $b "Talking to " 90 | Out-Null
    Send-Line $b "hello chat"
    Wait-Out $a "received: hello chat" 30 | Out-Null
    Write-Output "PASS chat"
    exit 0
} catch {
    Write-Output "FAIL chat"
    Write-Output $_
    exit 1
} finally {
    Stop-Nodes
}
