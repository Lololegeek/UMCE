[CmdletBinding()]
param(
    [string]$Token = $env:MODRINTH_TOKEN,
    [string]$ProjectSlug = 'umce',
    [string]$Version = '0.1.0-alpha.1'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$artifact = Join-Path $repositoryRoot "platforms\fabric-26.3\build\libs\umce-fabric-26.3-$Version.jar"
$descriptionFile = Join-Path $repositoryRoot 'docs\MODRINTH_DESCRIPTION.md'
$manifestPath = 'fabric.mod.json'
$baseUri = 'https://api.modrinth.com/v2'
$userAgent = "UMCE/$Version (Modrinth release setup)"

if ([string]::IsNullOrWhiteSpace($Token)) {
    throw 'Set MODRINTH_TOKEN to a Modrinth API token with project-create and version-create permissions.'
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
    $manifestEntry = $archive.GetEntry($manifestPath)
    if ($null -eq $manifestEntry) { throw "Artifact does not contain $manifestPath" }
    $manifestStream = $manifestEntry.Open()
    try {
        $manifestReader = [System.IO.StreamReader]::new($manifestStream)
        try { $manifest = $manifestReader.ReadToEnd() | ConvertFrom-Json }
        finally { $manifestReader.Dispose() }
    } finally { $manifestStream.Dispose() }

    if ($manifest.id -ne 'umce_fabric_26_3' -or $manifest.version -ne $Version) {
        throw "Artifact metadata mismatch: found mod '$($manifest.id)' version '$($manifest.version)', expected UMCE/$Version."
    }
    if ($manifest.depends.minecraft -ne '26.3') {
        throw "Artifact metadata targets unexpected Minecraft version '$($manifest.depends.minecraft)'."
    }
    if (-not ($archive.Entries | Where-Object FullName -like 'META-INF/jars/*')) {
        throw 'Artifact is missing its nested UMCE API/core runtime dependencies.'
    }
} finally {
    $archive.Dispose()
}

$headers = @{
    Authorization = $Token
    'User-Agent' = $userAgent
}
$slugUri = "$baseUri/project/$ProjectSlug"
try {
    $existingProject = Invoke-RestMethod -Uri $slugUri -Headers @{ 'User-Agent' = $userAgent }
    throw "Modrinth slug '$ProjectSlug' already exists (project $($existingProject.id)); refusing to modify another project."
} catch {
    if ($_.Exception.Message -like "Modrinth slug '$ProjectSlug' already exists*") { throw }
    $statusCode = 0
    if ($null -ne $_.Exception.Response) { $statusCode = [int]$_.Exception.Response.StatusCode }
    if ($statusCode -ne 404) { throw "Could not verify Modrinth slug '$ProjectSlug' is free (HTTP $statusCode)." }
}

$description = Get-Content -LiteralPath $descriptionFile -Raw
$projectData = @{
    slug = $ProjectSlug
    title = 'UMCE'
    description = 'Server-side diagnostics and tick profiling for Minecraft Java 26.3 on Fabric.'
    body = $description
    categories = @('fabric', 'optimization', 'technology')
    project_type = 'mod'
    environment = @('server_only')
    license_id = 'MIT'
    is_draft = $true
}
$projectJson = ConvertTo-Json -InputObject $projectData -Depth 16
$project = Invoke-RestMethod -Uri "$baseUri/project" -Method Post -Headers $headers `
    -ContentType 'application/json' -Body $projectJson

try {
    $fabricApi = Invoke-RestMethod -Uri "$baseUri/project/fabric-api" -Headers @{ 'User-Agent' = $userAgent }
    $versionData = @{
        name = "UMCE $Version - Fabric 26.3"
        version_number = $Version
        changelog = (Get-Content -LiteralPath (Join-Path $repositoryRoot 'CHANGELOG.md') -Raw)
        dependencies = @(@{ project_id = $fabricApi.id; dependency_type = 'required' })
        game_versions = @('26.3')
        version_type = 'alpha'
        loaders = @('fabric')
        featured = $false
        status = 'draft'
        project_id = $project.id
        file_parts = @('modfile')
        primary_file = 'modfile'
        environment = 'server_only'
    }

    $httpClient = [System.Net.Http.HttpClient]::new()
    $httpClient.DefaultRequestHeaders.Add('Authorization', $Token)
    $httpClient.DefaultRequestHeaders.Add('User-Agent', $userAgent)
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $fileStream = [System.IO.File]::OpenRead($artifact)
    try {
        $jsonContent = [System.Net.Http.StringContent]::new(
            (ConvertTo-Json -InputObject $versionData -Depth 16 -Compress),
            [System.Text.Encoding]::UTF8,
            'application/json')
        $multipart.Add($jsonContent, 'data')
        $fileContent = [System.Net.Http.StreamContent]::new($fileStream)
        $fileContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('application/java-archive')
        $multipart.Add($fileContent, 'modfile', [System.IO.Path]::GetFileName($artifact))

        $response = $httpClient.PostAsync("$baseUri/version", $multipart).GetAwaiter().GetResult()
        $responseText = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        if (-not $response.IsSuccessStatusCode) {
            throw "Version upload failed (HTTP $([int]$response.StatusCode)): $responseText"
        }
        $uploadedVersion = $responseText | ConvertFrom-Json
    } finally {
        $fileStream.Dispose()
        $multipart.Dispose()
        $httpClient.Dispose()
    }
} catch {
    Write-Error "The project draft was created, but release upload failed. Project id: $($project.id); slug: $ProjectSlug. $($_.Exception.Message)"
    throw
}

$checksum = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Output "Modrinth project draft created: $ProjectSlug (id $($project.id))"
Write-Output "Modrinth version draft uploaded: $Version (id $($uploadedVersion.id))"
Write-Output "Artifact: $artifact"
Write-Output "SHA-256: $checksum"
Write-Output "Draft URL: https://modrinth.com/mod/$ProjectSlug"
