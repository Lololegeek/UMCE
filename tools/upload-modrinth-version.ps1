[CmdletBinding()]
param(
    [string]$Token = $env:MODRINTH_TOKEN,
    [string]$ProjectSlug = 'umce',
    [string]$Version = '0.1.0-alpha.2'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$artifact = Join-Path $root "platforms\fabric-1.21.1\build\libs\umce-fabric-1.21.1-$Version.jar"
$descriptionFile = Join-Path $root 'docs\MODRINTH_DESCRIPTION.md'
$baseUri = 'https://api.modrinth.com/v2'
$userAgent = "UMCE/$Version (Modrinth release setup)"

if ([string]::IsNullOrWhiteSpace($Token)) {
    throw 'Set MODRINTH_TOKEN to a Modrinth API token with project and version write permissions.'
}
if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
    throw "Release artifact not found: $artifact. Run the Gradle build first."
}
if (-not (Test-Path -LiteralPath $descriptionFile -PathType Leaf)) {
    throw "Modrinth description not found: $descriptionFile"
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($artifact)
try {
    $entry = $archive.GetEntry('fabric.mod.json')
    if ($null -eq $entry) { throw 'Artifact does not contain fabric.mod.json.' }
    $stream = $entry.Open()
    try {
        $reader = [System.IO.StreamReader]::new($stream)
        try { $manifest = $reader.ReadToEnd() | ConvertFrom-Json }
        finally { $reader.Dispose() }
    } finally { $stream.Dispose() }
    if ($manifest.id -ne 'umce_fabric_1_21_1' -or $manifest.version -ne $Version -or $manifest.depends.minecraft -ne '1.21.1') {
        throw "Artifact metadata mismatch: expected UMCE/$Version for Minecraft 1.21.1."
    }
    if (-not ($archive.Entries | Where-Object FullName -like 'META-INF/jars/*')) {
        throw 'Artifact is missing its nested UMCE API/core runtime dependencies.'
    }
} finally { $archive.Dispose() }

$headers = @{ Authorization = $Token; 'User-Agent' = $userAgent }
$projectUri = "$baseUri/project/$ProjectSlug"
$project = $null
try { $project = Invoke-RestMethod -Uri $projectUri -Headers $headers }
catch {
    $status = 0
    if ($null -ne $_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
    if ($status -ne 404) { throw "Could not verify Modrinth project '$ProjectSlug' (HTTP $status)." }
}

$description = Get-Content -LiteralPath $descriptionFile -Raw
$summary = 'Server-side diagnostics and tick profiling for Minecraft Java 1.21.1 on Fabric.'
if ($null -eq $project) {
    $projectData = @{
        slug = $ProjectSlug; title = 'UMCE'; description = $summary; body = $description
        categories = @('optimization', 'technology', 'utility'); project_type = 'mod'
        environment = @('server_only'); client_side = 'unsupported'; server_side = 'required'
        license_id = 'MIT'; is_draft = $true; initial_versions = @()
    }
    $projectClient = [System.Net.Http.HttpClient]::new()
    $projectClient.DefaultRequestHeaders.Add('Authorization', $Token)
    $projectClient.DefaultRequestHeaders.Add('User-Agent', $userAgent)
    $projectForm = [System.Net.Http.MultipartFormDataContent]::new()
    try {
        $projectContent = [System.Net.Http.StringContent]::new(
            (ConvertTo-Json -InputObject $projectData -Depth 16),
            [System.Text.Encoding]::UTF8,
            'application/json')
        $projectForm.Add($projectContent, 'data')
        $projectResponse = $projectClient.PostAsync("$baseUri/project", $projectForm).GetAwaiter().GetResult()
        $projectResponseText = $projectResponse.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $projectResponse.IsSuccessStatusCode) {
            throw "Project creation failed (HTTP $([int]$projectResponse.StatusCode)): $projectResponseText"
        }
        $project = $projectResponseText | ConvertFrom-Json
    } finally {
        $projectForm.Dispose()
        $projectClient.Dispose()
    }
} else {
    $projectData = @{ description = $summary; body = $description }
    $null = Invoke-RestMethod -Uri $projectUri -Method Patch -Headers $headers `
        -ContentType 'application/json' -Body (ConvertTo-Json -InputObject $projectData -Depth 16)
}

$existingVersions = @(Invoke-RestMethod -Uri "$baseUri/project/$($project.id)/version" -Headers $headers)
if (@($existingVersions | Where-Object { $_.version_number -eq $Version }).Count -gt 0) {
    throw "Modrinth version $Version already exists on '$ProjectSlug'; refusing to duplicate it."
}

$fabricApi = Invoke-RestMethod -Uri "$baseUri/project/fabric-api" -Headers @{ 'User-Agent' = $userAgent }
$fullChangelog = Get-Content -LiteralPath (Join-Path $root 'CHANGELOG.md') -Raw
$changelogMatch = [regex]::Match(
    $fullChangelog,
    "(?ms)^##\s+$([regex]::Escape($Version))\s*\r?\n(.*?)(?=^##\s+|\z)")
if (-not $changelogMatch.Success) { throw "CHANGELOG.md has no section for $Version." }
$versionData = @{
    name = "UMCE $Version - Fabric 1.21.1"
    version_number = $Version
    changelog = $changelogMatch.Groups[1].Value.Trim()
    dependencies = @(@{ project_id = $fabricApi.id; dependency_type = 'required' })
    game_versions = @('1.21.1')
    version_type = 'alpha'
    loaders = @('fabric')
    featured = $false
    status = 'listed'
    project_id = $project.id
    file_parts = @('modfile')
    primary_file = 'modfile'
    environment = 'server_only'
}

$client = [System.Net.Http.HttpClient]::new()
$client.DefaultRequestHeaders.Add('Authorization', $Token)
$client.DefaultRequestHeaders.Add('User-Agent', $userAgent)
$form = [System.Net.Http.MultipartFormDataContent]::new()
$fileStream = [System.IO.File]::OpenRead($artifact)
try {
    $jsonContent = [System.Net.Http.StringContent]::new(
        (ConvertTo-Json -InputObject $versionData -Depth 16 -Compress),
        [System.Text.Encoding]::UTF8,
        'application/json')
    $form.Add($jsonContent, 'data')
    $fileContent = [System.Net.Http.StreamContent]::new($fileStream)
    $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('application/java-archive')
    $form.Add($fileContent, 'modfile', [System.IO.Path]::GetFileName($artifact))
    $response = $client.PostAsync("$baseUri/version", $form).GetAwaiter().GetResult()
    $responseText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        throw "Version upload failed (HTTP $([int]$response.StatusCode)): $responseText"
    }
    $uploaded = $responseText | ConvertFrom-Json
} finally {
    $fileStream.Dispose()
    $form.Dispose()
    $client.Dispose()
}

$legacy = @($existingVersions | Where-Object {
    $_.version_number -eq '0.1.0-alpha.1' -and
    $_.game_versions -contains '26.3' -and
    @($_.files | Where-Object filename -like 'umce-fabric-26.3-*.jar').Count -gt 0
})
foreach ($old in $legacy) {
    try {
        $null = Invoke-RestMethod -Uri "$baseUri/version/$($old.id)" -Method Delete -Headers $headers
        Write-Output "Removed superseded Minecraft 26.3 draft version: $($old.id)"
    } catch {
        Write-Warning "Could not remove superseded Minecraft 26.3 version $($old.id); inspect Modrinth manually. $($_.Exception.Message)"
    }
}

$checksum = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "Modrinth project: $ProjectSlug (id $($project.id))"
Write-Output "Modrinth version uploaded: $($uploaded.version_number) (id $($uploaded.id), status $($uploaded.status))"
Write-Output "Game version: $($uploaded.game_versions -join ', ') / loader: $($uploaded.loaders -join ', ')"
Write-Output "Artifact: $artifact"
Write-Output "SHA-256: $checksum"
Write-Output "Project URL: https://modrinth.com/mod/$ProjectSlug"
