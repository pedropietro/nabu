# Shared helpers for the example tests. Dot-source from the PowerShell scripts.
# Leaves node processes registered so Stop-Nodes can end them.

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$Work = Join-Path $Root "target\example-tests"
New-Item -ItemType Directory -Force -Path $Work | Out-Null
Set-Location $Root

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "mvn is not on PATH."
}

$script:Nodes = @()

function Stop-Nodes {
    foreach ($n in @($script:Nodes)) {
        try {
            if ($null -ne $n.Proc -and -not $n.Proc.HasExited) {
                & taskkill.exe /T /F /PID $n.Proc.Id | Out-Null
            }
        } catch {
            Write-Output "could not stop $($n.Name): $_"
        }
    }
    $script:Nodes = @()
    Start-Sleep -Seconds 2
}

function Ensure-Compiled {
    & mvn -f (Join-Path $Root "examples\pom.xml") -q compile
    if ($LASTEXITCODE -ne 0) { throw "example compile failed" }
}

function ConvertTo-JavaPath([string]$Path) {
    return ($Path -replace '\\', '/')
}

# Start an example and keep stdin open.
function Start-Node([string]$Name, [string]$Main, [string]$ExecArgs) {
    $out = Join-Path $Work "$Name.out"
    if (Test-Path $out) { Remove-Item $out -Force }
    $pom = Join-Path $Root "examples\pom.xml"
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c mvn -f `"$pom`" -q exec:java `"-Dexec.mainClass=$Main`" `"-Dexec.args=$ExecArgs`" > `"$out`" 2>&1"
    $psi.WorkingDirectory = $Root
    $psi.RedirectStandardInput = $true
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    [void]$proc.Start()
    $node = [pscustomobject]@{ Name = $Name; Proc = $proc; Out = $out }
    $script:Nodes += $node
    return $node
}

function Send-Line($Node, [string]$Line) {
    # BaseStream avoids the StreamWriter preamble. A BOM would become the first character of the line.
    $bytes = [System.Text.Encoding]::ASCII.GetBytes("$Line`n")
    $Node.Proc.StandardInput.BaseStream.Write($bytes, 0, $bytes.Length)
    $Node.Proc.StandardInput.BaseStream.Flush()
}

function Wait-Out($Node, [string]$Pattern, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $Node.Out) {
            $text = Get-Content -Path $Node.Out -Raw -ErrorAction SilentlyContinue
            if ($text -and ($text -match $Pattern)) { return $text }
        }
        if ($Node.Proc.HasExited) {
            $text = ""
            if (Test-Path $Node.Out) { $text = Get-Content -Path $Node.Out -Raw -ErrorAction SilentlyContinue }
            throw "process $($Node.Name) exited while waiting for /$Pattern/`n$text"
        }
        Start-Sleep -Milliseconds 300
    }
    $text = ""
    if (Test-Path $Node.Out) { $text = Get-Content -Path $Node.Out -Raw -ErrorAction SilentlyContinue }
    throw "timeout waiting for /$Pattern/ in $($Node.Name)`n$text"
}

function Wait-Count($Node, [string]$Literal, [int]$Needed, [int]$Seconds) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        $text = ""
        if (Test-Path $Node.Out) { $text = Get-Content -Path $Node.Out -Raw -ErrorAction SilentlyContinue }
        if ($text -and ([regex]::Matches($text, [regex]::Escape($Literal))).Count -ge $Needed) { return $text }
        if ($Node.Proc.HasExited) { throw "process $($Node.Name) exited while counting $Literal`n$text" }
        Start-Sleep -Milliseconds 300
    }
    $text = ""
    if (Test-Path $Node.Out) { $text = Get-Content -Path $Node.Out -Raw -ErrorAction SilentlyContinue }
    throw "timeout counting $Literal in $($Node.Name)`n$text"
}

function Get-Field($Node, [string]$Label) {
    $text = Get-Content -Path $Node.Out -Raw
    $match = [regex]::Matches($text, "(?m)^${Label}: (\S+)") | Select-Object -Last 1
    if (-not $match) { throw "no ${Label} in $($Node.Name)" }
    return $match.Groups[1].Value
}

function Write-Bytes([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text)
}

function Get-NabuJar {
    $jar = Get-ChildItem (Join-Path $Root "target\nabu-*-jar-with-dependencies.jar") -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $jar) { throw "Build Nabu first: mvn package -DskipTests" }
    return $jar.FullName
}
