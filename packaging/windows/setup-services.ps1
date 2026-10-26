<#
.SYNOPSIS
    Run by the installer (as administrator) after copying the files: database, configuration,
    services, firewall. Safe to run again: an update keeps the database and the configuration.

.DESCRIPTION
    Data lives outside the program folder, in %ProgramData%\Swift Invoicing:
        database\   PostgreSQL data (service SwiftInvoicingDB, port 5442, local connections only)
        config\     swift-invoicing.properties (port, database password), postgres-admin.txt
        backups\    daily backups (Réglages > Sauvegardes)
        logs\       application, service and installation logs (installation.log)
    Only administrators and the services can read it: it holds every invoice and the passwords.
#>
param(
    [Parameter(Mandatory = $true)][string]$InstallDir,
    # Port of the application; 0 keeps the configured one (update)
    [int]$Port = 0,
    # Lets the other PCs of the office reach the application (private and domain networks only)
    [switch]$OpenFirewall,
    [string]$DataDir = (Join-Path $env:ProgramData 'Swift Invoicing')
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$DbService = 'SwiftInvoicingDB'
$AppService = 'SwiftInvoicing'
$DbPort = 5442
$FirewallRule = 'Swift Invoicing'

$PgBin = Join-Path $InstallDir 'pgsql\bin'
$DbDir = Join-Path $DataDir 'database'
$ConfigDir = Join-Path $DataDir 'config'
$ConfigFile = Join-Path $ConfigDir 'swift-invoicing.properties'
$LogDir = Join-Path $DataDir 'logs'

# Well-known accounts by SID: their names change with the language of Windows
$System = '*S-1-5-18'
$Administrators = '*S-1-5-32-544'
$NetworkService = '*S-1-5-20'

function Step($message) { Write-Host "--- $message" }

# Runs a program; its output goes to the installation log, a failure stops the installation
function Invoke-Native([string]$exe, [string[]]$arguments) {
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $exe @arguments 2>&1 | ForEach-Object { Write-Host "    $_" }
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($LASTEXITCODE -ne 0) {
        throw "$([System.IO.Path]::GetFileName($exe)) a échoué (code $LASTEXITCODE)."
    }
}

# Letters and digits only: nothing to escape in the properties file or a connection URL
function New-Password([int]$length = 32) {
    $chars = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'.ToCharArray()
    $bytes = New-Object byte[] $length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
}

function Get-ConfigValue([string]$key) {
    if (-not (Test-Path $ConfigFile)) { return $null }
    $line = Get-Content $ConfigFile | Where-Object { $_ -like "$key=*" } | Select-Object -Last 1
    if ($line) { return $line.Substring($key.Length + 1) }
    return $null
}

# Java reads .properties as ISO-8859-1: ASCII only (paths with forward slashes, no escaping)
function Set-ConfigValue([string]$key, [string]$value) {
    $lines = @()
    if (Test-Path $ConfigFile) { $lines = @(Get-Content $ConfigFile | Where-Object { $_ -notlike "$key=*" }) }
    $lines += "$key=$value"
    Set-Content -Path $ConfigFile -Value $lines -Encoding ASCII
}

function Wait-Until([scriptblock]$condition, [int]$seconds, [string]$failure) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if (& $condition) { return }
        Start-Sleep -Seconds 2
    }
    throw $failure
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Start-Transcript -Path (Join-Path $LogDir 'installation.log') -Append | Out-Null
$exitCode = 0
try {
    Write-Host "Swift Invoicing : installation dans $InstallDir, données dans $DataDir ($(Get-Date -Format s))"

    Step 'Dossier des données (accès réservé aux administrateurs)'
    foreach ($dir in $DataDir, $ConfigDir, (Join-Path $DataDir 'backups'), $LogDir) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    # On the folder only: everything inside inherits it, the database keeps its own grants
    Invoke-Native 'icacls.exe' @($DataDir, '/inheritance:r', '/grant:r', "${System}:(OI)(CI)F", "${Administrators}:(OI)(CI)F", '/Q')

    Step 'Bibliothèques Microsoft Visual C++ (nécessaires à PostgreSQL)'
    $runtime = 'vcruntime140.dll', 'vcruntime140_1.dll', 'msvcp140.dll' |
        Where-Object { -not (Test-Path (Join-Path $env:SystemRoot "System32\$_")) }
    if ($runtime) {
        $redist = Start-Process -FilePath (Join-Path $InstallDir 'redist\vc_redist.x64.exe') -ArgumentList '/install', '/quiet', '/norestart' -Wait -PassThru
        # 1638: a newer version is installed, 3010: installed, restart recommended
        if ($redist.ExitCode -notin 0, 1638, 3010) { throw "Installation de Visual C++ impossible (code $($redist.ExitCode))." }
    } else {
        Write-Host '    Déjà installées.'
    }

    $fresh = -not (Test-Path (Join-Path $DbDir 'PG_VERSION'))
    $superPassword = $null
    if ($fresh) {
        Step 'Création de la base de données'
        if ((Test-Path $DbDir) -and (Get-ChildItem $DbDir -Force | Select-Object -First 1)) {
            throw "Le dossier $DbDir n'est pas vide mais ne contient pas de base PostgreSQL : déplacez-le puis relancez l'installation."
        }
        New-Item -ItemType Directory -Force -Path $DbDir | Out-Null
        # initdb drops the administrator rights, so the installing account is named explicitly;
        # the database service runs as NETWORK SERVICE
        $me = '*' + [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
        Invoke-Native 'icacls.exe' @($DbDir, '/grant', "${me}:(OI)(CI)F", "${NetworkService}:(OI)(CI)F", '/Q')

        $superPassword = New-Password
        $pwFile = Join-Path $ConfigDir 'initdb-password.tmp'
        Set-Content -Path $pwFile -Value $superPassword -Encoding ASCII -NoNewline
        try {
            $initdb = Join-Path $PgBin 'initdb.exe'
            $common = @('-D', $DbDir, '-U', 'postgres', "--pwfile=$pwFile", '--auth=scram-sha-256', '--encoding=UTF8')
            try {
                # French sorting of names (accents), from the ICU library shipped with PostgreSQL
                Invoke-Native $initdb ($common + @('--locale-provider=icu', '--icu-locale=fr-FR', '--locale=C'))
            } catch {
                Write-Host "    ICU indisponible ($_) : tri binaire."
                Get-ChildItem $DbDir -Force | Remove-Item -Recurse -Force
                Invoke-Native $initdb ($common + @('--locale=C'))
            }
        } finally {
            Remove-Item $pwFile -Force -ErrorAction SilentlyContinue
        }
        Add-Content -Path (Join-Path $DbDir 'postgresql.conf') -Encoding ASCII -Value @(
            '',
            '# Swift Invoicing: local connections only, on its own port',
            "port = $DbPort",
            "listen_addresses = 'localhost'",
            'logging_collector = on',
            "log_directory = 'log'",
            "log_filename = 'postgresql-%a.log'",
            'log_truncate_on_rotation = on',
            'log_rotation_age = 1d')
        # For support only: the application uses its own account
        Set-Content -Path (Join-Path $ConfigDir 'postgres-admin.txt') -Encoding ASCII -Value @(
            'Compte administrateur PostgreSQL de Swift Invoicing (support technique uniquement)',
            "Port: $DbPort", 'Utilisateur: postgres', "Mot de passe: $superPassword")
    }

    Step 'Service de la base de données'
    if (-not (Get-Service $DbService -ErrorAction SilentlyContinue)) {
        Invoke-Native (Join-Path $PgBin 'pg_ctl.exe') @('register', '-N', $DbService, '-D', $DbDir, '-U', 'NT AUTHORITY\NetworkService', '-S', 'auto', '-w')
        Invoke-Native 'sc.exe' @('description', $DbService, 'Base de données PostgreSQL de Swift Invoicing.')
    }
    Start-Service $DbService
    $pgIsReady = Join-Path $PgBin 'pg_isready.exe'
    Wait-Until { & $pgIsReady -h localhost -p $DbPort -q; $LASTEXITCODE -eq 0 } 90 'La base de données ne démarre pas : voir database\log.'

    if ($fresh) {
        Step "Compte de l'application dans la base"
        $appPassword = New-Password
        $env:PGPASSWORD = $superPassword
        try {
            Invoke-Native (Join-Path $PgBin 'psql.exe') @('-h', 'localhost', '-p', "$DbPort", '-U', 'postgres', '-d', 'postgres', '-X', '-q',
                '-v', 'ON_ERROR_STOP=1', '-c', "CREATE ROLE invoicing LOGIN PASSWORD '$appPassword'", '-c', 'CREATE DATABASE invoicing OWNER invoicing')
        } finally {
            Remove-Item Env:\PGPASSWORD -ErrorAction SilentlyContinue
        }
        Set-ConfigValue 'spring.datasource.url' "jdbc:postgresql://localhost:$DbPort/invoicing"
        Set-ConfigValue 'spring.datasource.username' 'invoicing'
        Set-ConfigValue 'spring.datasource.password' $appPassword
    } elseif (-not (Get-ConfigValue 'spring.datasource.password')) {
        throw "La base existe mais $ConfigFile ne contient pas son mot de passe : restaurez ce fichier puis relancez l'installation."
    }

    Step 'Configuration'
    if ($Port -gt 0) {
        Set-ConfigValue 'server.port' "$Port"
    } elseif (-not (Get-ConfigValue 'server.port')) {
        Set-ConfigValue 'server.port' '7070'
    }
    $Port = [int](Get-ConfigValue 'server.port')
    Set-ConfigValue 'app.backup.tools-directory' ($PgBin -replace '\\', '/')

    Step "Service de l'application"
    $winsw = Join-Path $InstallDir 'service\SwiftInvoicing-service.exe'
    if (-not (Get-Service $AppService -ErrorAction SilentlyContinue)) {
        Invoke-Native $winsw @('install')
    }
    Start-Service $AppService

    Step 'Pare-feu Windows'
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & netsh.exe advfirewall firewall delete rule name="$FirewallRule" 2>&1 | Out-Null
    $ErrorActionPreference = $previous
    if ($OpenFirewall) {
        Invoke-Native 'netsh.exe' @('advfirewall', 'firewall', 'add', 'rule', "name=$FirewallRule", 'dir=in', 'action=allow', 'protocol=TCP',
            "localport=$Port", 'profile=private,domain')
    } else {
        Write-Host '    Accès depuis ce PC seulement.'
    }

    Step "Démarrage de l'application (jusqu'à 3 minutes)"
    Wait-Until {
        try { (Invoke-WebRequest "http://localhost:$Port/actuator/health" -UseBasicParsing -TimeoutSec 5).StatusCode -eq 200 } catch { $false }
    } 180 "L'application ne répond pas sur le port $Port : voir $LogDir."
    Write-Host "Swift Invoicing est prêt : http://localhost:$Port"
} catch {
    Write-Host "ERREUR : $_"
    $exitCode = 1
} finally {
    Stop-Transcript | Out-Null
}
exit $exitCode
