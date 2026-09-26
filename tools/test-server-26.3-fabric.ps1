[CmdletBinding()]
param(
    [string]$JavaHome,
    [string]$GradleUserHome,
    [ValidateRange(30, 900)]
    [int]$StartupTimeoutSeconds = 240
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$moduleRoot = Join-Path $repositoryRoot 'platforms\fabric-26.3'
$runDirectory = Join-Path $moduleRoot 'build\server-run'
$testOutput = Join-Path $repositoryRoot 'build\test-server-26.3-fabric.log'
$gradleWrapper = Join-Path $repositoryRoot 'gradlew.bat'

if (-not $JavaHome) {
    $JavaHome = $env:JAVA_HOME
}
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin\java.exe'))) {
    throw 'Set JAVA_HOME to a Java 25 JDK or pass -JavaHome <path>.'
}
$javaVersion = (& (Join-Path $JavaHome 'bin\java.exe') -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "(?:25|2[6-9]|[3-9][0-9])\.') {
    throw "Minecraft 26.3 needs Java 25 or newer. Detected: $javaVersion"
}

if (-not $GradleUserHome) {
    $GradleUserHome = $env:GRADLE_USER_HOME
}
if (-not $GradleUserHome) {
    $GradleUserHome = Join-Path $repositoryRoot 'build\.gradle-user-home'
}

New-Item -ItemType Directory -Force -Path $runDirectory, (Split-Path -Parent $testOutput), $GradleUserHome | Out-Null
Set-Content -LiteralPath (Join-Path $runDirectory 'eula.txt') -Encoding ascii -Value 'eula=true'
Set-Content -LiteralPath (Join-Path $runDirectory 'server.properties') -Encoding ascii -Value @(
    'server-ip=127.0.0.1',
    'server-port=25577',
    'online-mode=false',
    'max-players=1',
    'view-distance=3',
    'simulation-distance=3',
    'spawn-protection=0',
    'level-name=umce-fabric-26_3-smoke',
    'motd=UMCE automated integration test'
)

$processInfo = [System.Diagnostics.ProcessStartInfo]::new()
$processInfo.FileName = $env:ComSpec
$processInfo.Arguments = '/d /s /c ""' + $gradleWrapper + '" --no-daemon --console=plain -g "' + $GradleUserHome + '" :platforms:fabric-26.3:runServer"'
$processInfo.WorkingDirectory = $repositoryRoot
$processInfo.UseShellExecute = $false
$processInfo.CreateNoWindow = $true
$processInfo.RedirectStandardInput = $true
$processInfo.RedirectStandardOutput = $true
$processInfo.RedirectStandardError = $true
$processInfo.Environment['JAVA_HOME'] = $JavaHome
$processInfo.Environment['GRADLE_USER_HOME'] = $GradleUserHome

$process = [System.Diagnostics.Process]::new()
$process.StartInfo = $processInfo
$processStarted = $false
$outputWriter = [System.IO.StreamWriter]::new($testOutput, $false, [System.Text.UTF8Encoding]::new($false))
$script:serverReady = $false
$script:commandRegistered = $false
$script:statusReturned = $false
$script:stopSent = $false
$script:statusSentAt = $null
$clock = [System.Diagnostics.Stopwatch]::StartNew()

function Write-ServerLine([string]$Line) {
    $outputWriter.WriteLine($Line)
    $outputWriter.Flush()
    Write-Host $Line

    if ($Line -match 'Done \([^)]*\)! For help') {
        $script:serverReady = $true
    }
    if ($Line -match 'UMCE admin command registered: /umce status') {
        $script:commandRegistered = $true
    }
    if ($Line -match 'System chat: UMCE \| MC 26\.3 \|') {
        $script:statusReturned = $true
    }
}

try {
    if (-not $process.Start()) { throw 'Could not start Gradle server integration run.' }
    $processStarted = $true
    $stdout = $process.StandardOutput.ReadLineAsync()
    $stderr = $process.StandardError.ReadLineAsync()

    while ($true) {
        if ($stdout.IsCompleted) {
            $line = $stdout.GetAwaiter().GetResult()
            if ($null -ne $line) {
                Write-ServerLine $line
                $stdout = $process.StandardOutput.ReadLineAsync()
            }
        }
        if ($stderr.IsCompleted) {
            $line = $stderr.GetAwaiter().GetResult()
            if ($null -ne $line) {
                Write-ServerLine $line
                $stderr = $process.StandardError.ReadLineAsync()
            }
        }

        if ($script:serverReady -and $script:commandRegistered -and -not $script:statusSentAt) {
            $process.StandardInput.WriteLine('umce status')
            $process.StandardInput.Flush()
            $script:statusSentAt = [DateTimeOffset]::UtcNow
        }
        if ($script:statusReturned -and -not $script:stopSent -and
                (([DateTimeOffset]::UtcNow - $script:statusSentAt).TotalSeconds -ge 2)) {
            $process.StandardInput.WriteLine('stop')
            $process.StandardInput.Flush()
            $script:stopSent = $true
        }

        if ($clock.Elapsed.TotalSeconds -gt $StartupTimeoutSeconds -and -not $script:serverReady) {
            throw "Server did not become ready within $StartupTimeoutSeconds seconds. See $testOutput"
        }
        if ($script:stopSent -and $clock.Elapsed.TotalSeconds -gt ($StartupTimeoutSeconds + 90)) {
            throw "Server did not stop cleanly within 90 seconds. See $testOutput"
        }
        if ($process.HasExited -and $stdout.IsCompleted -and $stderr.IsCompleted) { break }
        Start-Sleep -Milliseconds 100
    }

    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "Gradle/server process exited with code $($process.ExitCode). See $testOutput" }
    if (-not $script:serverReady) { throw 'Server readiness marker was not observed.' }
    if (-not $script:commandRegistered) { throw 'UMCE admin command did not register.' }
    if (-not $script:statusReturned) { throw 'The /umce status command did not return UMCE runtime data.' }
    if (-not $script:stopSent) { throw 'The test did not send the server stop command.' }
    if (-not (Select-String -LiteralPath $testOutput -Pattern 'UMCE tick profile: samples=[1-9][0-9]*' -Quiet)) {
        throw 'UMCE did not record tick samples before shutdown.'
    }

    Write-Host "Minecraft 26.3 Fabric server smoke test passed. Log: $testOutput"
} finally {
    if ($processStarted -and -not $process.HasExited) {
        try {
            $process.StandardInput.WriteLine('stop')
            $process.StandardInput.Flush()
            if (-not $process.WaitForExit(15000)) { $process.Kill($true) }
        } catch {
            if (-not $process.HasExited) { $process.Kill($true) }
        }
    }
    $outputWriter.Dispose()
    $process.Dispose()
    $clock.Stop()
}
