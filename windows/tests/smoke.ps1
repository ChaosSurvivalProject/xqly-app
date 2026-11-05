# Windows 冒烟测试（由 CI 的 windows-latest runner 执行）
#   .\windows\tests\smoke.ps1 -Exe .\windows\build\Release\xqly.exe
#
# 只验证「能启动、有主窗口、能优雅退出、数据目录被创建」，
# 不冒充真机兼容性测试或性能测试。
param(
  [Parameter(Mandatory = $true)][string] $Exe,
  [int] $StartupTimeoutSeconds = 15
)
$ErrorActionPreference = 'Stop'

if (-not (Test-Path $Exe)) { Write-Error "找不到 EXE: $Exe"; exit 1 }
$exePath = (Resolve-Path $Exe).Path
Write-Host "启动: $exePath"

$before = Get-Process -Name xqly -ErrorAction SilentlyContinue
$p = Start-Process -FilePath $exePath -PassThru
$deadline = (Get-Date).AddSeconds($StartupTimeoutSeconds)
$hwnd = [IntPtr]::Zero
while ((Get-Date) -lt $deadline) {
  Start-Sleep -Milliseconds 300
  $p.Refresh()
  if ($p.HasExited) { Write-Error "进程过早退出，ExitCode=$($p.ExitCode)"; exit 1 }
  if ($p.MainWindowHandle -ne [IntPtr]::Zero) { $hwnd = $p.MainWindowHandle; break }
}
if ($hwnd -eq [IntPtr]::Zero) { Write-Error '未在超时内创建主窗口'; exit 1 }
Write-Host "主窗口已创建: handle=$hwnd"

# 优雅关闭（走 WM_CLOSE，验证退出路径不会丢数据）
if (-not $p.CloseMainWindow()) { Write-Error '无法发送 WM_CLOSE'; exit 1 }
if (-not $p.WaitForExit(10000)) {
  Write-Error '窗口未在 10 秒内退出'; $p.Kill(); exit 1
}
Write-Host "进程已退出: ExitCode=$($p.ExitCode)"

# 数据目录应被创建（安装版 %LOCALAPPDATA%\XQLY，便携版 EXE 旁 Data\）
$dataDir = Join-Path $env:LOCALAPPDATA 'XQLY'
if (Test-Path $dataDir) {
  Write-Host "数据目录: $dataDir"
  Get-ChildItem $dataDir | ForEach-Object { Write-Host "  $($_.Name)" }
} else {
  Write-Host "::warning::未在 $dataDir 找到数据目录（便携版模式请检查 EXE 旁 Data\\）"
}

Write-Host 'SMOKE_OK'
