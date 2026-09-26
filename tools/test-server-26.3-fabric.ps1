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
$configDirectory = Join-Path $runDirectory 'config'
$configFile = Join-Path $configDirectory 'umce.properties'
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

New-Item -ItemType Directory -Force -Path $runDirectory, $configDirectory,
    (Split-Path -Parent $testOutput), $GradleUserHome | Out-Null
Set-Content -LiteralPath (Join-Path $runDirectory 'eula.txt') -Encoding ascii -Value 'eula=true'
Set-Content -LiteralPath $configFile -Encoding ascii -Value @(
    'profile=balanced',
    'cpu.workers=1',
    'cpu.queueCapacity=32',
    'gpu.enabled=false',
    'dashboard.enabled=false',
    'dashboard.bind=127.0.0.1',
    'compatibility.safeUnknownMods=true'
)
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
$script:commandInputs = @('umce help', 'umce status', 'umce hardware', 'umce profile', 'umce compat', 'umce mods', 'umce memory', 'umce gpu', 'umce workers', 'umce reload')
$script:commandMarkers = @(
    'System chat: UMCE commands: /umce status',
    'System chat: UMCE \| MC 26\.3 \| fabric 0\.19\.5 \| profile balanced \|',
    'System chat: UMCE hardware \|',
    'System chat: UMCE tick profile \|',
    'System chat: UMCE compatibility \|',
    'System chat: UMCE mods \| loaded [1-9][0-9]* \|',
    'System chat: UMCE memory \| heap used [0-9.]+ MiB',
    'System chat: UMCE GPU \| compute probe NOT_PROBED \| configured false \| backend NOT_INSTALLED \| workloads disabled',
    'System chat: UMCE workers \| configured CPU workers 1 \| queue capacity 32 \| server scheduler work is not attached yet',
    'System chat: UMCE configuration reloaded \| profile smoke_reloaded \|'
)
$script:commandResponses = [bool[]]::new($script:commandInputs.Length)
$script:nextCommandIndex = 0
$script:stopSent = $false
$script:allCommandsReturnedAt = $null
$clock = [System.Diagnostics.Stopwatch]::StartNew()

function Write-ServerLine([string]$Line) {
    $outputWriter.WriteLine($Line)
    $outputWriter.Flush()
    Write-Host $Line

    if ($Line -match 'Done \([^)]*\)! For help') {
        $script:serverReady = $true
    }
    if ($Line -match 'UMCE admin commands registered: /umce status, hardware, profile, compat, mods, memory, gpu, workers, reload') {
        $script:commandRegistered = $true
    }
    for ($index = 0; $index -lt $script:commandMarkers.Length; $index++) {
        if ($Line -match $script:commandMarkers[$index]) {
            $script:commandResponses[$index] = $true
        }
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

        if ($script:serverReady -and $script:commandRegistered -and $script:nextCommandIndex -eq 0) {
            $process.StandardInput.WriteLine($script:commandInputs[$script:nextCommandIndex])
            $process.StandardInput.Flush()
            $script:nextCommandIndex++
        }
        if ($script:nextCommandIndex -gt 0 -and
                $script:commandResponses[$script:nextCommandIndex - 1] -and
                $script:nextCommandIndex -lt $script:commandInputs.Length) {
            $nextCommand = $script:commandInputs[$script:nextCommandIndex]
            if ($nextCommand -eq 'umce reload') {
                if (-not (Test-Path -LiteralPath $configFile)) {
                    throw "Expected UMCE configuration file was not created: $configFile"
                }
                $configContents = Get-Content -LiteralPath $configFile -Raw
                if ($configContents -notmatch '(?m)^profile=') {
                    throw 'The generated UMCE configuration has no profile property.'
                }
                $configContents = $configContents -replace '(?m)^profile=.*$', 'profile=smoke_reloaded'
                Set-Content -LiteralPath $configFile -Encoding ascii -Value $configContents
            }
            $process.StandardInput.WriteLine($nextCommand)
            $process.StandardInput.Flush()
            $script:nextCommandIndex++
        }
        if ($script:nextCommandIndex -eq $script:commandInputs.Length -and
                $script:commandResponses[$script:commandInputs.Length - 1] -and
                -not $script:allCommandsReturnedAt) {
            $script:allCommandsReturnedAt = [DateTimeOffset]::UtcNow
        }
        if ($script:allCommandsReturnedAt -and -not $script:stopSent -and
                (([DateTimeOffset]::UtcNow - $script:allCommandsReturnedAt).TotalSeconds -ge 2)) {
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
    for ($index = 0; $index -lt $script:commandResponses.Length; $index++) {
        if (-not $script:commandResponses[$index]) {
            throw "The command '$($script:commandInputs[$index])' did not return its expected UMCE response."
        }
    }
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
