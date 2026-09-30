[CmdletBinding()]
param([string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'build\vm-metrics-agent'
$classes = Join-Path $output 'classes'
New-Item -ItemType Directory -Force $classes | Out-Null
& (Join-Path $JavaHome 'bin\javac.exe') --release 21 -d $classes (Join-Path $PSScriptRoot 'java\VmMetricsAgent.java')
if ($LASTEXITCODE -ne 0) { throw 'VM metric agent compilation failed.' }
$manifest = Join-Path $output 'MANIFEST.MF'
Set-Content -LiteralPath $manifest -Encoding ascii -Value "Premain-Class: io.umce.benchmark.VmMetricsAgent`n"
$jar = Join-Path $output 'umce-vm-metrics-agent.jar'
& (Join-Path $JavaHome 'bin\jar.exe') --create --file $jar --manifest $manifest -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'VM metric agent packaging failed.' }
Write-Output $jar
