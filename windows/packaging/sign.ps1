# 星穹旅驿 Windows 代码签名（CI / 维护者使用）
#
#   $env:WINDOWS_SIGNING_PFX      = <受限 Secret 指向的 .pfx 路径>
#   $env:WINDOWS_SIGNING_PASSWORD = <受限 Secret，日志中必须遮蔽>
#   .\sign.ps1 -Files xqly.exe, xqly-setup.msix
#
# 规则：
#   * EXE、DLL、安装器与便携版主程序都签名，并加 RFC 3161 时间戳；
#   * 证书/密码绝不写入仓库或日志，只从受限 Secrets / 签名服务读取；
#   * 签名完成后不得再次修改包体；
#   * 没有正式证书时**不伪造签名**，明确返回「未签名」并让 Release 标注测试版。
param(
  [Parameter(Mandatory = $true)][string[]] $Files,
  [string] $TimestampUrl = "http://timestamp.digicert.com"
)
$ErrorActionPreference = 'Stop'

$pfx  = $env:WINDOWS_SIGNING_PFX
$pass = $env:WINDOWS_SIGNING_PASSWORD

if ([string]::IsNullOrWhiteSpace($pfx) -or -not (Test-Path $pfx) -or
    [string]::IsNullOrWhiteSpace($pass)) {
  Write-Host "::warning::未配置 WINDOWS_SIGNING_PFX / WINDOWS_SIGNING_PASSWORD"
  Write-Host "::warning::产物保持未签名，必须以 *-unsigned / *-test-signed 命名并在 Release 标注"
  Write-Host "SIGN_RESULT=UNSIGNED"
  exit 0
}

# 定位 signtool：优先 CI 环境变量，其次 Windows SDK 安装目录。
$signtool = $env:SIGNTOOL_PATH
if ([string]::IsNullOrWhiteSpace($signtool)) {
  $kits = Join-Path ${env:ProgramFiles(x86)} 'Windows Kits\10\bin'
  if (Test-Path $kits) {
    $signtool = Get-ChildItem -Path $kits -Filter 'signtool.exe' -Recurse -ErrorAction SilentlyContinue |
                Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
  }
}
if ([string]::IsNullOrWhiteSpace($signtool) -or -not (Test-Path $signtool)) {
  Write-Error '未找到 signtool.exe，请安装 Windows SDK 或设置 SIGNTOOL_PATH'
  exit 1
}

foreach ($f in $Files) {
  if (-not (Test-Path $f)) { Write-Error "待签名文件不存在: $f"; exit 1 }
  # 密码只作为进程参数传入，绝不 Write-Host / Write-Output。
  & $signtool sign /f "$pfx" /p "$pass" /fd SHA256 `
      /tr "$TimestampUrl" /td SHA256 /v "$f" 2>&1 |
      Where-Object { $_ -notmatch 'password|/p ' } | Write-Host
  if ($LASTEXITCODE -ne 0) { Write-Error "签名失败: $f"; exit 1 }

  # 验证签名与时间戳（像用户一样验证）。
  & $signtool verify /pa /v "$f" 2>&1 | Write-Host
  if ($LASTEXITCODE -ne 0) { Write-Error "签名验证失败: $f"; exit 1 }

  $hash = (Get-FileHash -Algorithm SHA256 -Path $f).Hash.ToLowerInvariant()
  Write-Host "SIGNED  $f  sha256=$hash"
}

Write-Host 'SIGN_RESULT=SIGNED'
