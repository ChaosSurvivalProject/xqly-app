; 星穹旅驿 · 签名 EXE 安装器（MSIX 不兼容时的备选方案）
;
; 采用原因记录：若 MSIX 与 WebView2 固定版本运行时的自定义安装步骤、
; 自托管更新或文件关联冲突，改用此 Inno Setup 安装器并单独记录原因。
; 构建：ISCC.exe windows\packaging\installer\xqly.iss
;
; 不强制管理员权限；用户数据放 %LOCALAPPDATA%\XQLY；不静默安装任何运行时、
; 浏览器插件或第三方软件。安装器与其中的 EXE/DLL 由 sign.ps1 签名。
[Setup]
AppId={{8E4A2D16-3B7C-4F0A-9D2E-6C1B5A7F3E90}
AppName=星穹旅驿
AppVersion=2.0.1
AppVerName=星穹旅驿 2.0.1
AppPublisher=星穹旅驿运营团队
AppPublisherURL=https://xqly.xt91tv.shop
AppSupportURL=https://github.com/ChaosSurvivalProject/xqly-app/issues
DefaultDirName={autopf}\星穹旅驿
DefaultGroupName=星穹旅驿
DisableProgramGroupPage=yes
OutputDir=..\..\..\dist
OutputBaseFilename=星穹旅驿-2.0.1-windows-x64-setup
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
UninstallDisplayIcon={app}\xqly.exe
CloseApplications=no
; 覆盖升级保留用户数据，不删除收藏 / 设置 / 阅读进度 / 游戏存档
; （这些都在 {localappdata}\XQLY，不在 {app} 下）

[Languages]
Name: "chinesesimplified"; MessagesFile: "compiler:Languages\ChineseSimplified.isl"

[Files]
; 路径相对于本 .iss 所在目录，构建前由 CI 把 Release 产物放入 dist\windows-x64\
Source: "..\..\..\dist\windows-x64\xqly.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\..\dist\windows-x64\*.dll"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\..\dist\windows-x64\data\*"; DestDir: "{app}\data"; Flags: ignoreversion recursesubdirs createallsubdirs
Source: "..\..\..\LICENSE"; DestDir: "{app}"; Flags: ignoreversion
Source: "..\..\..\NOTICE.md"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\星穹旅驿"; Filename: "{app}\xqly.exe"
Name: "{autodesktop}\星穹旅驿"; Filename: "{app}\xqly.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "创建桌面快捷方式"; GroupDescription: "附加任务:"; Flags: unchecked

[Run]
Filename: "{app}\xqly.exe"; Description: "立即启动星穹旅驿"; Flags: nowait postinstall skipifsilent

[Code]
// 卸载时不静默删除用户数据：只提示位置，由用户在设置中「清理用户数据」二次确认。
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
    MsgBox('卸载完成。收藏、阅读进度与游戏存档保留在 %LOCALAPPDATA%\XQLY，'
           + '如需彻底删除请在应用设置中执行「清理用户数据」（需二次确认）。',
           mbInformation, MB_OK);
end;
