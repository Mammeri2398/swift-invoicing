; Installer of Swift Invoicing for Windows, compiled by build.ps1 (Inno Setup 6):
;   ISCC.exe /DAppVersion=1.0.0 /DStageDir=<files prepared by build.ps1> swift-invoicing.iss
;
; Installs the program in Program Files, then runs scripts\setup-services.ps1, which creates the
; database (first install only), the configuration and the two Windows services. Running a newer
; installer over an installed version updates the program and keeps the data.

#ifndef AppVersion
  #define AppVersion "0.0.1"
#endif
#ifndef StageDir
  #define StageDir "..\..\target\windows\stage"
#endif

[Setup]
; Never change the AppId: it is how an update finds the installed version
AppId={{4B409481-C302-4DAD-8391-DABBB74CAD92}
AppName=Swift Invoicing
AppVersion={#AppVersion}
AppVerName=Swift Invoicing {#AppVersion}
AppPublisher=Swift Labs
VersionInfoVersion={#AppVersion}
DefaultDirName={autopf}\Swift Invoicing
DefaultGroupName=Swift Invoicing
DisableProgramGroupPage=yes
OutputBaseFilename=SwiftInvoicing-Setup-{#AppVersion}
Compression=lzma2/max
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
MinVersion=10.0
PrivilegesRequired=admin
WizardStyle=modern
UninstallDisplayName=Swift Invoicing
UninstallDisplayIcon={app}\app\SwiftInvoicing.exe
SetupLogging=yes
; The services are stopped by PrepareToInstall, not by the Restart Manager
CloseApplications=no

[Languages]
Name: "french"; MessagesFile: "compiler:Languages\French.isl"

[Tasks]
Name: "firewall"; Description: "Autoriser l'accès depuis les autres ordinateurs du bureau (pare-feu Windows, réseaux privés)"
Name: "desktopicon"; Description: "Créer un raccourci sur le bureau"

[Files]
Source: "{#StageDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[INI]
Filename: "{app}\Swift Invoicing.url"; Section: "InternetShortcut"; Key: "URL"; String: "http://localhost:{code:GetPort}/"

[Icons]
Name: "{group}\Swift Invoicing"; Filename: "{app}\Swift Invoicing.url"; IconFilename: "{app}\app\SwiftInvoicing.exe"
Name: "{group}\Sauvegardes et journaux"; Filename: "{commonappdata}\Swift Invoicing"
Name: "{autodesktop}\Swift Invoicing"; Filename: "{app}\Swift Invoicing.url"; IconFilename: "{app}\app\SwiftInvoicing.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Swift Invoicing.url"; Description: "Ouvrir Swift Invoicing"; Flags: postinstall shellexec nowait skipifsilent; Check: Started

[UninstallRun]
; The data in ProgramData (database, backups, configuration) is kept on purpose
Filename: "{sys}\WindowsPowerShell\v1.0\powershell.exe"; Parameters: "-NoProfile -ExecutionPolicy Bypass -File ""{app}\scripts\remove-services.ps1"" -InstallDir ""{app}"""; Flags: runhidden waituntilterminated; RunOnceId: "RemoveServices"

[UninstallDelete]
Type: files; Name: "{app}\Swift Invoicing.url"

[Messages]
FinishedLabel=Swift Invoicing est installé et démarre avec Windows. Les autres ordinateurs du bureau y accèdent par leur navigateur (adresse affichée sur la page de connexion).

[Code]
var
  PortPage: TInputQueryWizardPage;
  SetupFailed: Boolean;

function PowerShell: String;
begin
  Result := ExpandConstant('{sys}\WindowsPowerShell\v1.0\powershell.exe');
end;

function ConfigFile: String;
begin
  Result := ExpandConstant('{commonappdata}\Swift Invoicing\config\swift-invoicing.properties');
end;

{ The port of an installed version, empty on a first install }
function ConfiguredPort: String;
var
  Lines: TArrayOfString;
  I: Integer;
begin
  Result := '';
  if LoadStringsFromFile(ConfigFile, Lines) then
    for I := 0 to GetArrayLength(Lines) - 1 do
      if Pos('server.port=', Lines[I]) = 1 then
        Result := Trim(Copy(Lines[I], Length('server.port=') + 1, 10));
end;

function IsUpdate: Boolean;
begin
  Result := ConfiguredPort <> '';
end;

function GetPort(Param: String): String;
begin
  Result := ConfiguredPort;
  if Result = '' then
    Result := IntToStr(StrToIntDef(Trim(PortPage.Values[0]), 7070));
end;

function Started: Boolean;
begin
  Result := not SetupFailed;
end;

procedure InitializeWizard;
begin
  PortPage := CreateInputQueryPage(wpSelectTasks, 'Port de l''application',
    'Adresse de Swift Invoicing dans le navigateur',
    'Les utilisateurs ouvriront http://NOM-DE-CE-PC:port. Gardez 7070, sauf si un autre logiciel utilise déjà ce port.');
  PortPage.Add('Port :', False);
  PortPage.Values[0] := '7070';
end;

{ An update keeps the port chosen at the first install }
function ShouldSkipPage(PageID: Integer): Boolean;
begin
  Result := (PageID = PortPage.ID) and IsUpdate;
end;

function NextButtonClick(CurPageID: Integer): Boolean;
var
  Port: Integer;
begin
  Result := True;
  if CurPageID = PortPage.ID then
  begin
    Port := StrToIntDef(Trim(PortPage.Values[0]), 0);
    if (Port < 1024) or (Port > 65535) or (Port = 5442) then
    begin
      MsgBox('Indiquez un port entre 1024 et 65535 (5442 est utilisé par la base de données).', mbError, MB_OK);
      Result := False;
    end;
  end;
end;

{ An update replaces files in use: the application and its database are stopped first }
function PrepareToInstall(var NeedsRestart: Boolean): String;
var
  ResultCode: Integer;
begin
  Result := '';
  Exec(PowerShell, '-NoProfile -Command "Stop-Service SwiftInvoicing -Force -ErrorAction SilentlyContinue; ' +
    'Stop-Service SwiftInvoicingDB -Force -ErrorAction SilentlyContinue"', '', SW_HIDE, ewWaitUntilTerminated, ResultCode);
end;

procedure CurStepChanged(CurStep: TSetupStep);
var
  Params: String;
  ResultCode: Integer;
begin
  if CurStep = ssPostInstall then
  begin
    WizardForm.StatusLabel.Caption := 'Démarrage de la base de données et de l''application (jusqu''à quelques minutes)...';
    Params := '-NoProfile -ExecutionPolicy Bypass -File "' + ExpandConstant('{app}\scripts\setup-services.ps1') +
      '" -InstallDir "' + ExpandConstant('{app}') + '"';
    if not IsUpdate then
      Params := Params + ' -Port ' + GetPort('');
    if WizardIsTaskSelected('firewall') then
      Params := Params + ' -OpenFirewall';
    if not Exec(PowerShell, Params, '', SW_HIDE, ewWaitUntilTerminated, ResultCode) or (ResultCode <> 0) then
    begin
      SetupFailed := True;
      MsgBox('Swift Invoicing est installé mais n''a pas pu démarrer.' + #13#10 + #13#10 +
        'Le détail est dans le journal :' + #13#10 + ExpandConstant('{commonappdata}\Swift Invoicing\logs\installation.log'),
        mbError, MB_OK);
    end;
  end;
end;
