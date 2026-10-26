# Windows installer

`SwiftInvoicing-Setup-<version>.exe` installs Swift Invoicing on a customer's PC or small server.
Everything it needs is inside: the application with its own Java, PostgreSQL, and the Microsoft
C++ runtime. Nothing else has to be installed, and the customer never uses a command line.

## Building it

The installer can only be built on Windows. Two ways:

**GitHub Actions** (recommended): `.github/workflows/windows-installer.yml`
- Tag a version and push the tag:
  ```bash
  git tag v1.0.0
  git push dev v1.0.0
  ```
  The tests run first. Then the installer is built on a GitHub Windows machine and attached to a GitHub release named `v1.0.0`.
- Or start the workflow by hand: **Actions → Windows installer → Run workflow**. The installer is then a downloadable artifact of the run, with the pom's version.

**On a Windows PC**, with JDK 17 and [Inno Setup 6](https://jrsoftware.org/isdl.php) installed:
```powershell
powershell -ExecutionPolicy Bypass -File packaging\windows\build.ps1 -Version 1.0.0
```
The result is `target\windows\SwiftInvoicing-Setup-1.0.0.exe`. Downloads are cached in `target\windows\cache`.

| File | Role |
|---|---|
| `build.ps1` | Builds the jar, packages it with its Java runtime (jpackage), adds PostgreSQL, WinSW and Visual C++, then compiles the installer |
| `swift-invoicing.iss` | The installer itself (Inno Setup): screens, files, shortcuts, update and uninstall |
| `setup-services.ps1` | Run by the installer: database, configuration, services, firewall |
| `remove-services.ps1` | Run by the uninstaller: removes the services and the firewall rule |
| `SwiftInvoicing-service.xml` | The application's Windows service (WinSW) |

The PostgreSQL and WinSW versions are pinned in `build.ps1`, with their SHA-256 checksums. To update one, change the version and the checksum together.

## What the installer does

1. Asks for the port (7070 by default) and whether the office's other PCs may connect (Windows firewall, private and domain networks only).
2. Copies the program to `C:\Program Files\Swift Invoicing`: `app\` (application and Java), `pgsql\` (PostgreSQL), `service\`, `scripts\`, `redist\`.
3. On the first install only:
   - creates the database in `C:\ProgramData\Swift Invoicing\database`, with French sorting (ICU) and random passwords;
   - writes `config\swift-invoicing.properties`, which `application-onprem.properties` reads.
4. Registers and starts two services, both starting with Windows:
   - `SwiftInvoicingDB`: PostgreSQL on port 5442, reachable from this PC only;
   - `SwiftInvoicing`: the application.
5. Waits until the application answers, then offers to open it in the browser (the setup wizard).

`C:\ProgramData\Swift Invoicing` is readable only by administrators and the services, because it holds every invoice and the passwords. Inside it:

| Folder | Contents |
|---|---|
| `database\` | PostgreSQL data (its own log in `database\log`) |
| `config\` | `swift-invoicing.properties`; `postgres-admin.txt` (database administrator password, for support) |
| `backups\` | Daily backups (Réglages → Sauvegardes) |
| `logs\` | `swift-invoicing.log`, the service's console output, `installation.log` |

**Update:** run the newer installer.
- It stops the services, replaces the program and starts them again.
- The database, the configuration and the port are kept.
- Flyway updates the schema when the application starts.
- Moving to a new major version of PostgreSQL (e.g. 16 → 17) would need a data migration, so stay on 16.x for updates.

**Uninstall:** removes the program, the services and the firewall rule. The data in `C:\ProgramData\Swift Invoicing` is kept on purpose, and a reinstall finds it again.

## Testing a new installer

Use a clean Windows 10/11 virtual machine, with a snapshot to return to:
1. Install, then check:
   - the browser opens on the setup wizard;
   - `services.msc` shows *Swift Invoicing* and *SwiftInvoicingDB* as running and automatic;
   - another PC on the network reaches `http://<pc-name>:7070`.
2. Make a backup, then restore it from Réglages → Sauvegardes.
3. Restart Windows: the application must come back on its own.
4. Install the same or a newer version over it: the data must still be there.
5. Uninstall, then check that `C:\ProgramData\Swift Invoicing` is still there.

If something fails, the installer names `logs\installation.log`. The service's console output and `swift-invoicing.log` are in the same folder.

## Before selling

- **Signing:** the installer is not signed, so Windows SmartScreen warns about it ("Windows a protégé votre ordinateur" → *Informations complémentaires* → *Exécuter quand même*). A code-signing certificate removes that warning. Sign `SwiftInvoicing-Setup-*.exe` (and ideally `app\SwiftInvoicing.exe`) with `signtool` in `build.ps1`.
- **Icon:** the application uses Java's default icon. Add an `.ico` with `--icon` (jpackage) and `SetupIconFile` (Inno Setup).
