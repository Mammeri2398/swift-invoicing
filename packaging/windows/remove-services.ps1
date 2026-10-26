<#
.SYNOPSIS
    Run by the uninstaller: stops and removes the services and the firewall rule.
    The data (%ProgramData%\Swift Invoicing: database, backups, configuration) is kept on purpose:
    reinstalling finds it again. Delete that folder by hand to remove everything.
#>
param(
    [Parameter(Mandatory = $true)][string]$InstallDir
)

$ErrorActionPreference = 'Continue'

$winsw = Join-Path $InstallDir 'service\SwiftInvoicing-service.exe'
if (Get-Service 'SwiftInvoicing' -ErrorAction SilentlyContinue) {
    Stop-Service 'SwiftInvoicing' -Force -ErrorAction SilentlyContinue
    & $winsw uninstall
}
if (Get-Service 'SwiftInvoicingDB' -ErrorAction SilentlyContinue) {
    Stop-Service 'SwiftInvoicingDB' -Force -ErrorAction SilentlyContinue
    & (Join-Path $InstallDir 'pgsql\bin\pg_ctl.exe') unregister -N 'SwiftInvoicingDB'
}
& netsh.exe advfirewall firewall delete rule name="Swift Invoicing" | Out-Null
exit 0
