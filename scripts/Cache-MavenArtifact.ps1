param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$')][string]$Group,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$Artifact,
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9][A-Za-z0-9_.-]*$')][string]$Version,
    [string]$Proxy = '',
    [ValidateSet('repo.maven.apache.org', 'repo1.maven.org')][string]$CentralHost = 'repo.maven.apache.org'
)
$ErrorActionPreference = 'Stop'
if ($Artifact.Contains('..') -or $Version.Contains('..')) { throw 'Invalid coordinate.' }
if ($Proxy -and -not ([uri]$Proxy).IsLoopback) { throw 'Only an optional local proxy is supported.' }
$campusCacheRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\.local\maven-cache'))
$campusRelativePath = $Group.Replace('.', '/') + '/' + $Artifact + '/' + $Version
$campusDestination = [IO.Path]::GetFullPath((Join-Path $campusCacheRoot $campusRelativePath))
if (-not $campusDestination.StartsWith($campusCacheRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Coordinate leaves the cache directory.'
}
New-Item -ItemType Directory -Path $campusDestination -Force | Out-Null
$campusCurl = (Get-Command curl.exe -ErrorAction Stop).Source
foreach ($campusExtension in @('pom', 'jar')) {
    $campusName = $Artifact + '-' + $Version + '.' + $campusExtension
    $campusUrl = 'https://' + $CentralHost + '/maven2/' + $campusRelativePath + '/' + $campusName
    $campusFinal = Join-Path $campusDestination $campusName
    $campusPart = $campusFinal + '.part'
    $campusAlgorithm = 'SHA256'
    $campusChecksum = $campusFinal + '.sha256'
    $campusArguments = @('--fail', '--location', '--silent', '--show-error',
        '--retry', '2', '--retry-delay', '2', '--connect-timeout', '15', '--max-time', '120')
    if ($Proxy) { $campusArguments += @('--proxy', $Proxy) }
    $campusChecksumStatus = & $campusCurl @campusArguments '--write-out' '%{http_code}' '--output' $campusChecksum ($campusUrl + '.sha256')
    if ($LASTEXITCODE -ne 0) {
        if ($campusChecksumStatus -ne '404') { throw ('Official checksum unavailable: ' + $campusName) }
        # Older Central publications only have SHA-1 sidecars. HTTPS verification remains enabled.
        $campusAlgorithm = 'SHA1'
        $campusChecksum = $campusFinal + '.sha1'
        & $campusCurl @campusArguments '--output' $campusChecksum ($campusUrl + '.sha1')
        if ($LASTEXITCODE -ne 0) { throw ('Official checksum unavailable: ' + $campusName) }
    }
    $campusExpected = (Get-Content -LiteralPath $campusChecksum -Raw).Trim().Split(' ')[0]
    $campusHashLength = if ($campusAlgorithm -eq 'SHA256') { 64 } else { 40 }
    if ($campusExpected.Length -ne $campusHashLength -or $campusExpected -notmatch '^[A-Fa-f0-9]+$') { throw 'Invalid official checksum.' }
    if (Test-Path -LiteralPath $campusFinal) {
        if ((Get-FileHash -LiteralPath $campusFinal -Algorithm $campusAlgorithm).Hash -ine $campusExpected) {
            throw ('Existing cache does not match official checksum: ' + $campusName)
        }
    } else {
        & $campusCurl @campusArguments '--continue-at' '-' '--output' $campusPart $campusUrl
        if ($LASTEXITCODE -ne 0) { throw ('Download incomplete; partial file is not used: ' + $campusName) }
        if ((Get-FileHash -LiteralPath $campusPart -Algorithm $campusAlgorithm).Hash -ine $campusExpected) {
            throw ('Checksum mismatch; partial file is not used: ' + $campusName)
        }
        Move-Item -LiteralPath $campusPart -Destination $campusFinal
    }
    [pscustomobject]@{Artifact=$campusName; Verified=$true; PublisherAlgorithm=$campusAlgorithm; PublisherChecksum=$campusExpected; SHA256=(Get-FileHash -LiteralPath $campusFinal -Algorithm SHA256).Hash}
}
