<#
.SYNOPSIS
    Builds the Windows installer: SwiftInvoicing-Setup-<version>.exe in target\windows.

.DESCRIPTION
    Runs on Windows only (jpackage and Inno Setup make Windows files on Windows), from any folder:
        powershell -ExecutionPolicy Bypass -File packaging\windows\build.ps1 [-Version 1.2.0]

    1. Builds the application jar (tests are run by the CI before, or skip them here).
    2. Packages it with its own Java runtime (jpackage), so the customer installs no Java.
    3. Adds a portable PostgreSQL (server and tools, without pgAdmin and the documentation),
       the service wrapper WinSW and the Microsoft C++ runtime PostgreSQL needs.
    4. Compiles the installer with Inno Setup (swift-invoicing.iss).

    Downloads are kept in target\windows\cache and checked (SHA-256, or Microsoft's signature).
    Needs: JDK 17 (jpackage), Inno Setup 6 (ISCC.exe).
#>
param(
    # Version of the installer (1.2.0 or v1.2.0); default: the pom's version without -SNAPSHOT
    [string]$Version,
    # Build the jar without running the tests (the CI runs them in another job)
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'   # Invoke-WebRequest is very slow with the progress bar

# Pinned downloads: update the version and the checksum together
$PostgresVersion = '16.15-1'
$PostgresUrl = "https://get.enterprisedb.com/postgresql/postgresql-$PostgresVersion-windows-x64-binaries.zip"
$PostgresSha256 = '25e6fcdfb8caec38691bf461125e7564508760666f7b8e5dc6a5f0818f58f81e'
$WinswUrl = 'https://github.com/winsw/winsw/releases/download/v2.12.0/WinSW-x64.exe'
$WinswSha256 = '05b82d46ad331cc16bdc00de5c6332c1ef818df8ceefcd49c726553209b3a0da'
# Always the latest 2015-2022 runtime: checked by its Microsoft signature instead of a checksum
$VcRedistUrl = 'https://aka.ms/vs/17/release/vc_redist.x64.exe'

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Out = Join-Path $Root 'target\windows'
$Cache = Join-Path $Out 'cache'
$Stage = Join-Path $Out 'stage'

function Step($message) { Write-Host "==> $message" -ForegroundColor Cyan }

function Get-Download($url, $file, $sha256) {
    $path = Join-Path $Cache $file
    if (-not (Test-Path $path)) {
        Step "Downloading $file"
        Invoke-WebRequest -Uri $url -OutFile "$path.part" -UseBasicParsing
        Move-Item "$path.part" $path -Force
    }
    if ($sha256) {
        $actual = (Get-FileHash $path -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($actual -ne $sha256) {
            Remove-Item $path
            throw "Checksum mismatch for $file (got $actual): deleted, check the pinned version."
        }
    }
    return $path
}

# --- Version -------------------------------------------------------------------------------------
if (-not $Version) {
    $pom = [xml](Get-Content (Join-Path $Root 'pom.xml'))
    $Version = $pom.project.version
}
$Version = $Version.TrimStart('v') -replace '-SNAPSHOT$', ''
if ($Version -notmatch '^\d+(\.\d+){0,2}$') {
    throw "Version '$Version' is not numeric (1.2.0): jpackage and Windows need a numeric version."
}
Step "Swift Invoicing $Version"

New-Item -ItemType Directory -Force -Path $Cache | Out-Null
# Everything but the download cache is rebuilt
foreach ($dir in 'stage', 'jpackage', 'jpackage-input', 'pgsql-extract') {
    $path = Join-Path $Out $dir
    if (Test-Path $path) { Remove-Item $path -Recurse -Force }
}
New-Item -ItemType Directory -Force -Path $Stage | Out-Null

# --- 1. Application jar --------------------------------------------------------------------------
Step 'Building the application jar'
# No 'clean': it would delete the download cache in target\windows
$mvnArgs = @('-B', '-q', 'package')
if ($SkipTests) { $mvnArgs += '-DskipTests' }
Push-Location $Root
try {
    & (Join-Path $Root 'mvnw.cmd') @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven failed ($LASTEXITCODE)" }
} finally {
    Pop-Location
}
$jar = Get-ChildItem (Join-Path $Root 'target') -Filter 'swift-invoicing-*.jar' |
    Where-Object { $_.Name -notmatch '(plain|sources|javadoc)\.jar$' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { throw 'Application jar not found in target' }

# --- 2. Application with its own Java (jpackage) -------------------------------------------------
Step 'Packaging the application with its Java runtime (jpackage)'
$jpackageInput = Join-Path $Out 'jpackage-input'
New-Item -ItemType Directory -Force -Path $jpackageInput | Out-Null
Copy-Item $jar.FullName (Join-Path $jpackageInput 'swift-invoicing.jar')
$jpackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jpackage.exe' } else { 'jpackage' }
& $jpackage --type app-image `
    --name SwiftInvoicing `
    --app-version $Version `
    --vendor 'Swift Labs' `
    --icon (Join-Path $PSScriptRoot 'swift-invoicing.ico') `
    --input $jpackageInput `
    --main-jar swift-invoicing.jar `
    --dest (Join-Path $Out 'jpackage') `
    --win-console `
    --java-options '-Dspring.profiles.active=onprem' `
    --java-options '-Dfile.encoding=UTF-8' `
    --java-options '-Xmx768m'
if ($LASTEXITCODE -ne 0) { throw "jpackage failed ($LASTEXITCODE)" }
Move-Item (Join-Path $Out 'jpackage\SwiftInvoicing') (Join-Path $Stage 'app')

# --- 3. PostgreSQL, WinSW, C++ runtime -----------------------------------------------------------
$pgZip = Get-Download $PostgresUrl "postgresql-$PostgresVersion.zip" $PostgresSha256
Step 'Extracting PostgreSQL (server and tools only)'
$pgTemp = Join-Path $Out 'pgsql-extract'
if (Test-Path $pgTemp) { Remove-Item $pgTemp -Recurse -Force }
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($pgZip)
try {
    # pgAdmin (700 MB), the documentation, the C headers and StackBuilder are not needed
    $kept = $zip.Entries | Where-Object { $_.FullName -match '^pgsql/(bin|lib|share)/.+[^/]$' -or $_.FullName -match '^pgsql/[^/]*license[^/]*\.txt$' }
    foreach ($entry in $kept) {
        $target = Join-Path $pgTemp ($entry.FullName -replace '/', '\')
        New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
        [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
    }
} finally {
    $zip.Dispose()
}
Move-Item (Join-Path $pgTemp 'pgsql') (Join-Path $Stage 'pgsql')
Remove-Item $pgTemp -Recurse -Force

$serviceDir = Join-Path $Stage 'service'
New-Item -ItemType Directory -Force -Path $serviceDir | Out-Null
$winsw = Get-Download $WinswUrl 'WinSW-x64-2.12.0.exe' $WinswSha256
# WinSW reads the XML file of the same name next to it
Copy-Item $winsw (Join-Path $serviceDir 'SwiftInvoicing-service.exe')
Copy-Item (Join-Path $PSScriptRoot 'SwiftInvoicing-service.xml') $serviceDir

$vcRedist = Get-Download $VcRedistUrl 'vc_redist.x64.exe' $null
$signature = Get-AuthenticodeSignature $vcRedist
if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch 'O=Microsoft Corporation') {
    Remove-Item $vcRedist
    throw "vc_redist.x64.exe is not signed by Microsoft ($($signature.Status)): deleted."
}
$redistDir = Join-Path $Stage 'redist'
New-Item -ItemType Directory -Force -Path $redistDir | Out-Null
Copy-Item $vcRedist $redistDir

$scriptsDir = Join-Path $Stage 'scripts'
New-Item -ItemType Directory -Force -Path $scriptsDir | Out-Null
Copy-Item (Join-Path $PSScriptRoot 'setup-services.ps1'), (Join-Path $PSScriptRoot 'remove-services.ps1') $scriptsDir

# --- 4. Installer (Inno Setup) -------------------------------------------------------------------
Step 'Compiling the installer (Inno Setup)'
$iscc = (Get-Command 'ISCC.exe' -ErrorAction SilentlyContinue).Source
if (-not $iscc) {
    $iscc = @("${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe", "$env:ProgramFiles\Inno Setup 6\ISCC.exe", "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe") |
        Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $iscc) { throw 'Inno Setup 6 not found (ISCC.exe): install it from https://jrsoftware.org/isdl.php' }
& $iscc /Qp "/DAppVersion=$Version" "/DStageDir=$Stage" "/O$Out" (Join-Path $PSScriptRoot 'swift-invoicing.iss')
if ($LASTEXITCODE -ne 0) { throw "Inno Setup failed ($LASTEXITCODE)" }

$setup = Join-Path $Out "SwiftInvoicing-Setup-$Version.exe"
Step ("Done: $setup ({0:N0} MB)" -f ((Get-Item $setup).Length / 1MB))
