# Start a Nabu daemon and put the same bytes back through its HTTP API.
# Ports 4001 (swarm), 5001 (API) and 9100 (metrics) must be free.
. "$PSScriptRoot\lib.ps1"
$nabu = $null
try {
    Ensure-Compiled
    Write-Output "RUN api"
    $jar = Get-NabuJar
    $repo = Join-Path $Work "ipfs-repo"
    if (Test-Path $repo) { Remove-Item -Recurse -Force $repo }
    New-Item -ItemType Directory -Force -Path $repo | Out-Null
    $out = Join-Path $Work "nabu.out"
    if (Test-Path $out) { Remove-Item $out -Force }
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c java -cp `"$jar`" org.peergos.Nabu -IPFS_PATH `"$repo`" > `"$out`" 2>&1"
    $psi.WorkingDirectory = $Root
    $psi.RedirectStandardInput = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $nabu = New-Object System.Diagnostics.Process
    $nabu.StartInfo = $psi
    [void]$nabu.Start()
    $node = [pscustomobject]@{ Name = "api"; Proc = $nabu; Out = $out }
    $script:Nodes += $node
    Wait-Out $node "RPC API server" 180 | Out-Null

    $payload = Join-Path $Work "api.txt"
    Write-Bytes $payload "api-bytes"
    $clientArgs = "/ip4/127.0.0.1/tcp/5001 " + (ConvertTo-JavaPath $payload)
    $client = & mvn -f (Join-Path $Root "examples\pom.xml") -q exec:java `
        "-Dexec.mainClass=examples.ApiBlocks" `
        "-Dexec.args=$clientArgs" 2>&1 | Out-String
    if ($LASTEXITCODE -ne 0 -or $client -notmatch "block/get matches block/put") {
        throw "ApiBlocks failed`n$client"
    }
    Write-Output "PASS api"
    exit 0
} catch {
    Write-Output "FAIL api"
    Write-Output $_
    exit 1
} finally {
    Stop-Nodes
}
